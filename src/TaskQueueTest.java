import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class TaskQueueTest {
	List<Leader> leaders = new ArrayList<Leader>();
	List<Member> members = new ArrayList<Member>();
	
	double communicationtime = 0.0;
	
	void initialize(Sfmt rnd){
		List<Grid> grid = new ArrayList<Grid>();
		for(int i=0;i<51;i++){
			for(int j=0;j<51;j++){
				Grid g = new Grid(i,j);
				grid.add(g);
			}
		}
		
		Collections.shuffle(grid, new Random(13));
		
		for(int i=0;i<100;i++){
			Leader leader = new Leader(rnd);
			leader.setPosition(grid.get(i).x, grid.get(i).y);
			leaders.add(leader);
		}
		for(int i=100;i<500;i++){
			Member member = new Member(rnd);
			member.setPosition(grid.get(i).x, grid.get(i).y);
			members.add(member);
		}
		
		for(int i=0;i<leaders.size();i++){
			Leader leader = leaders.get(i);
			for(int j=0;j<members.size();j++){
				Member member = members.get(j);
				leader.setdistance(member);
			}
		}
		for(int i=0;i<members.size();i++){
			Member member = members.get(i);
			for(int j=0;j<leaders.size();j++){
				Leader leader = leaders.get(j);
				member.setdistance(leader);
			}
		}
		
	}
	
	void update(Environment e){
		
		for(int i=0;i<100;i++){
			Leader leader = leaders.get(i);
			leader.deagent.clear();
			for(int j=0;j<400;j++){
				Member member = members.get(j);
				if(leader.getthreshold() < leader.de[member.getmyid()]){
					leader.adddeagent(member);
				}
				leader.reducede(member.getmyid());
			}
			leader.updatedeagent();
		}
		for(int i=0;i<400;i++){
			Member member = members.get(i);
			member.deagent.clear();
			for(int j=0;j<100;j++){
				Leader leader = leaders.get(j);
				if(member.getthreshold() < member.de[leader.getmyid()]){
					member.adddeagent(leader);
				}
				member.reducede(leader.getmyid());
			}
			member.updatedeagent();
		}
		e.decrementdelay();
		communicationtime += e.checkdelay();
	}
	
	public void run(){
		Environment e = new Environment();
		Sfmt rnd = new Sfmt(17/*seed*/);
		initialize(rnd);
		PrintWriter pw = null;
		try{
			FileWriter fw = new FileWriter("test2.csv", false); 
            pw = new PrintWriter(new BufferedWriter(fw));
            pw.print("tick");
        	pw.print(",");
        	pw.print("excution task");
        	pw.println();
		}catch(IOException ex){
			System.out.println(ex);
		}
		PrintWriter pw1 = null;
		try{
			FileWriter fw1 = new FileWriter("communicationtime.csv", false); 
            pw1 = new PrintWriter(new BufferedWriter(fw1));
            pw1.print("tick");
        	pw1.print(",");
        	pw1.print("communication time");
        	pw1.println();
		}catch(IOException ex){
			System.out.println(ex);
		}
		int excutiontask = 0;
		int wastetask = 0;
		int sumOfExcutionTime = 0;
		int numOfExcutingTask = 0;
		
		for(int tick=0;tick<20001;tick++){
			System.out.println("tick: " + tick);
			Random r = new Random(17);
			Collections.shuffle(leaders, r);
			Collections.shuffle(members, r);
			e.addTask(rnd.NextPoisson(5)/*mu*/, rnd);
				
			//リーダの行動
			for(int i=0;i<leaders.size();i++){
				Leader ld = leaders.get(i);
				switch(ld.getPhase()){
				case 0:
					if(!e.TaskisEmpty()){//タスクがあれば
						//タスクを取得
						Task task = e.pushTask();
						//System.out.println("Subtask size is " + task.getSubTasks().size());
						//候補メンバーに送るメッセージを決める(e-greedy法)
						int p = eGreedy(rnd);
						List<MessagetoMember> messagestomember = null;
						if(p == 0){
							messagestomember = ld.selectmember(members, task);
						}else if(p == 1){
							System.out.println("Epsilon");
							messagestomember = ld.selectrandommember(members, task);
						}
						
						//候補メンバーが足りなければタスクは破棄
						if(messagestomember == null){
							//System.out.println("waste task due to lack of member " + ld.getmyid());
							wastetask++;
							break;
						}
						/*
						for (int j=0; j<messagestomember.size(); j++){
							System.out.println("send message from Leader " + messagestomember.get(j).getfrom().getmyid() + " to Member " + messagestomember.get(j).getto().getmyid() + " " + messagestomember.get(j).getsubtask() + " delay " + messagestomember.get(j).getdelay());
						}
						*/
						//メッセージを送る
						for(int j=0;j<messagestomember.size();j++){
							ld.sendmessagetomember(messagestomember.get(j), e);
						}
						ld.setphase(1);
					}
					break;
				case 1:
					//メッセージの返信を見て
					if(ld.waitreply() == 0){
						//全部返信がきててアロケーションできるなら
						ld.taskallocate(e);
						ld.setphase(2);
						//excutiontask++;
					}else if(ld.waitreply() == 1){
						//全部返信がきててアロケーションできないなら
						ld.failallocate(e);
						//System.out.println("waste task due to allocation " + ld.getmyid());
						wastetask++;
						ld.setphase(0);
						ld.clearall();
					}
					break;
				
				case 2:
					ld.reduceexcutiontime();
					if(ld.checkmyexcution() == 0){
						//System.out.println("phase 2");
						if(ld.checkexcution() == 0){
							ld.setphase(0);
							excutiontask++;
							ld.clearall();
						}
					}
					break;
				}
				
			}	
			
			//メンバの行動
			for(int i=0;i<members.size();i++){
				Member mem = members.get(i);
				if(mem.havemessage()){
					mem.setphase(0);
				}else{
					mem.setphase(1);
				}
				switch(mem.getPhase()){
				case 0:
					if(mem.havemessage()){//メッセージが来ていたら
						//来てるメッセージを取得
						List<MessagetoMember> messages = mem.getmessages();
						//メッセージから受理するメッセージを選ぶ
						int p = eGreedy(rnd);
						MessagetoMember decide = null;
						if(p == 0){
							decide = mem.decideMessage(messages);
						}else if(p == 1){
							System.out.println("Epsilon");
							decide = mem.decideRandomMessage(messages,rnd);
						}
						if(mem.taskqueue.size() > 4){
							decide = null;
						}
						//来てるメッセージ全ての返信をする
						mem.sendreplymessages(e, decide, messages);	
						//受理したメッセージがあれば次のphaseへ
						if(decide != null){
							//System.out.println("member next phase 1" + mem.getmyid());
							mem.setcondition(true);
							mem.setphase(1);
							mem.lastSelectTick = tick;
						}
						
						//メッセージ集合を初期化
						mem.clearmessages();
					}
					break;
				case 1:
					//allocationまち
					//System.out.println("phase 1");
					mem.taskexcution(e);
					break;
				}
				
			}	
			update(e);
			if(tick % 100 == 0){
				pw.print(tick);
	        	pw.print(",");
	        	pw.print(excutiontask);
	        	pw.print(",");
	        	pw.print((double)sumOfExcutionTime / numOfExcutingTask);
	        	pw.println();
	        	sumOfExcutionTime = 0;
				numOfExcutingTask = 0;
				excutiontask = 0;
			}
			if(tick % 100 == 0){
				pw1.print(tick);
	        	pw1.print(",");
	        	pw1.print(communicationtime / 100);	
	        	pw1.println();
	        	communicationtime = 0;
			}
		}
		System.out.println(wastetask);
		
		for(int i=0;i<100;i++){
			Collections.sort(leaders, new LeaderIdComparator());
			Leader leader = leaders.get(i);
			if(!leader.deagent.isEmpty()){
				System.out.println("number of leader " + leader.getmyid() + " deagent is " + leader.deagent.size());
				for(int j=0;j<leader.deagent.size();j++){
					Agent agent = leader.deagent.get(j);
					System.out.println("member " + agent.getmyid() + " de = " + leader.de[agent.getmyid()]);
				}
			}
		}
		
		for(int i=0;i<400;i++){
			Collections.sort(members, new MemberIdComparator());
			Member member = members.get(i);
			if(!member.deagent.isEmpty())
			System.out.println("number of member " + member.getmyid() + " deagent is " + member.deagent.size() + " " + member.averageOfCapability());
		}
		
		/*
		for(int i=0;i<400;i++){
			Member member = members.get(i);
			System.out.println("member " + member.getmyid() + " last active " + member.lasttick + " " +member.isactive());
		}
		*/
		pw.close();
		pw1.close();
	}
	
	public int eGreedy(Sfmt rnd) {
		int A;
        int randNum = rnd.NextInt(101);
        if (randNum <= 0.05 * 100.0) {
        	//eの確率
			A = rnd.NextInt(2);
        } else {
        	//(1-e)の確率
        	A = 0;
        }
        return A;
	}
}