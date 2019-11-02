package po;

public class Main {
	public static void main (String[] args) {
		PostOffice postoffice = new PostOffice();
		Person chris = new Person ("Chris");
		Person john = new Person("John");
		Mail m  = new Mail("John", "Dundee", "You have got a secret");
		
		postoffice.Attach(chris);
		postoffice.Attach(john);
		
		postoffice.addMail(m);
	}

}
