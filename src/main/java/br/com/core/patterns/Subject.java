package patterns;

public interface Subject {
    
    public void attach(Observer o); // add a new observer in the interesting list
    public void dettach(Observer o); // remove a observer from interesting list
    public void notifyObservers(); // notify the observer about something in the node

}
