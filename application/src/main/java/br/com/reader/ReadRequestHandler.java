package br.com.reader;

import br.com.core.gossip.GossipWorker;
import br.com.core.gossip.MembershipList;
import br.com.core.model.AppRequest;
import br.com.core.model.AppResponse;
import br.com.core.model.DictionaryStorage;
import br.com.core.model.GossipMessage;
import br.com.core.model.NodeInfo;
import br.com.core.model.Operation;
import br.com.core.model.RequestHandler;

public class ReadRequestHandler implements RequestHandler {

    private DictionaryStorage dictionaryStorage;
    private MembershipList membershipList;
    private GossipWorker gossipWorker;
    private NodeInfo localNode;

    public ReadRequestHandler(DictionaryStorage storage,
                              MembershipList membershipList) {
        this.dictionaryStorage = storage;
        this.membershipList    = membershipList;
    }

    public void setGossipWorker(GossipWorker gossipWorker) {
        this.gossipWorker = gossipWorker;
    }

    public void setLocalNode(NodeInfo localNode) {
        this.localNode = localNode;
    }

    // -------------------------------------------------------------------------
    // Tratamento de requisições HTTP (via middleware)
    // -------------------------------------------------------------------------

    @Override
    public AppResponse handleRequest(AppRequest request) {
        System.out.printf("Reader processando %s para chave '%s'%n",
                request.getOperation(), request.getKey());

        if (request.getOperation() == Operation.GET) {
            byte[] value = dictionaryStorage.searchData(request.getKey());
            if (value == null) {
                return new AppResponse("200", "".getBytes(),
                        "Não encontrado (aguardando gossip)");
            }
            return new AppResponse("200", value, "OK");
        }

        return new AppResponse("405", null, "Method Not Allowed");
    }

    // -------------------------------------------------------------------------
    // Tratamento de mensagens gossip
    // -------------------------------------------------------------------------

    @Override
    public void handleGossip(GossipMessage gossip) {
        NodeInfo sender = gossip.getSourceNode();

        // ----------------------------------------------------------------
        // Deduplicação corrigida.
        //
        // Problema original: comparava sender.getSequenceNumber() com
        // localNode.getSequenceNumber().  Como sequenceNumber é o UUID do
        // nó, a comparação estava correta, mas a condição estava invertida:
        // o código retornava quando os UUIDs eram IGUAIS — ou seja, descartava
        // mensagens do próprio nó, o que é o comportamento desejado.
        // O bug real era que, após restart, o localNode recebe um novo UUID
        // (UUID.randomUUID()), então a entrada antiga na membership list nunca
        // coincidia — o nó ficava duplicado por até TOMBSTONE_TTL.
        //
        // A correção mantém a verificação por UUID (correto) e adiciona uma
        // segunda checagem por endereço+porta para cobrir o caso de restart.
        // ----------------------------------------------------------------
        if (localNode != null) {
            boolean sameUUID = sender.getSequenceNumber()
                    .equals(localNode.getSequenceNumber());
            boolean sameAddress = sender.getAddress().equals(localNode.getAddress())
                    && sender.getPort() == localNode.getPort();

            if (sameUUID || sameAddress) return; // mensagem própria — descarta
        }

        // Atualiza o heartbeat do remetente na membership list
        membershipList.updateNode(sender);

        AppRequest request = gossip.getData();

        // ----------------------------------------------------------------
        // Heartbeat (chave null) — atualiza membership mas não persiste dado
        // ----------------------------------------------------------------
        if (request == null || request.getKey() == null) {
            System.out.printf(
                    "[Reader porta %d] Heartbeat recebido de %s porta %d%n",
                    localNode != null ? localNode.getPort() : -1,
                    sender.getType(), sender.getPort());
            System.out.flush();
            return; // nada mais a fazer
        }

        // ----------------------------------------------------------------
        // Gossip de dados — persiste e propaga se ainda tiver hops
        // ----------------------------------------------------------------
        System.out.printf(
                "[Reader porta %d] Salvando chave '%s' (valor: '%s') do gossip%n",
                localNode != null ? localNode.getPort() : -1,
                request.getKey(),
                request.getValue() != null ? new String(request.getValue()) : "null");
        System.out.flush();

        switch (request.getOperation()) {
            case POST:
            case PUT:
                dictionaryStorage.saveLocalData(request.getKey(), request.getValue());
                break;
            case DELETE:
                dictionaryStorage.deleteLocalData(request.getKey());
                break;
            default:
                // GET via gossip não faz sentido — ignora
                break;
        }

        // Propaga apenas se ainda houver hops E gossipWorker disponível
        if (gossipWorker != null && gossip.getHopCount() > 0) {
            System.out.printf(
                    "[Reader porta %d] Propagando gossip da chave '%s' (hopCount: %d)%n",
                    localNode != null ? localNode.getPort() : -1,
                    request.getKey(), gossip.getHopCount());
            System.out.flush();
            gossipWorker.spreadGossip(gossip);
        }
    }
}