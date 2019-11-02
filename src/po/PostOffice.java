package po;

import java.util.ArrayList;

public class PostOffice implements Subject {
	
	private ArrayList<Mail> allMail;
	private ArrayList<Observer> observers;
	
	public PostOffice() {
		allMail = new ArrayList<>();
		observers = new ArrayList<>();
	}
	
	public void addMail (Mail m) {
		allMail.add(m);
		Notify();
	}
	
	public ArrayList<Mail> getState() {
		
		return allMail;
	}

	@Override
	public void Attach(Observer o) {
		observers.add(o);
	}

	@Override
	public void Dettach(Observer o) {

		observers.remove(o);
	}

	@Override
	public void Notify() {

		for (int i=0; i<observers.size(); i++) {
			observers.get(i).update(this);
		}
	}

}
