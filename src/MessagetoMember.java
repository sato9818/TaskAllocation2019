
public class MessagetoMember extends Message{
	private Leader from;
	private Member to;
	
	private boolean havetask = false;
	
	MessagetoMember(Leader f, Member t, SubTask s){
		//タスクを受理するかしないか
		super(s, 0);
		from = f;
		to = t;
		setdelay(f,t);
	}
	
	MessagetoMember(Leader f, Member t, SubTask s, boolean th){
		//受理メッセージに対するタスクを与える
		super(s, 1);
		from = f;
		to = t;
		setdelay(f,t);
		havetask = th;
	}
	
	MessagetoMember(Leader f, Member t, boolean ht){
		//受理メッセージに対するタスクを与えるか与えない
		super(null, 1);
		from = f;
		to = t;
		setdelay(f,t);
		havetask = ht;
	}
	
	
	public Leader getfrom(){
		return from;
	}
	
	public Member getto(){
		return to;
	}
	
	public boolean taskisallocated(){
		return havetask;
	}
	
}
