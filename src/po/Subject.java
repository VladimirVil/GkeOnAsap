package po;

public interface Subject {
	
	void Attach(Observer o);
	void Dettach(Observer o);
	void Notify();

}

