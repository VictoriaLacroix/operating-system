package nachos.threads;

import nachos.machine.*;

/** 
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
	private int message;	//our message for the communicator
	Lock ourLock;			//Communicator lock
	Condition2 toSpeak;		//Our Condition2 for our speaker
	Condition2 toListen;		//Our Condition2 for our listener
	int listenerWaiting = 0;//Variable for how many listeners waiting
	int speakerWaiting = 0;	//Variable for how many speakers waiting
	int speakerActive = 0;	//Variable for how many speakers active
	int listenerActive = 0;	//Variable for how many listeners active
	boolean messageActive = false; //Our Boolean to see if theres a message active
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
		ourLock = new Lock();
		toSpeak = new Condition2(ourLock);
		toListen = new Condition2(ourLock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
		ourLock.acquire();	
		speakerWaiting++;
		while(listenerWaiting==0||speakerActive > 0 || listenerActive > 0 || messageActive){
			toSpeak.sleep();//Sleep if theres no listeners waiting or if theres a speaker/listener active
		}	
		speakerWaiting--;	//decrement the speakers waiting
		speakerActive++; 	//increment the active speakers
		ourLock.release(); //release the lock so we can transfer the message
		
		message = word;		//Transfer word to message
		
		ourLock.acquire();	//Reacquire ourLock to decrement the active speakers and have an activeMessage
		messageActive = true;
		speakerActive--;
		toListen.wake();	//Wake up a listener so it can listen to the word
		ourLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
		ourLock.acquire();	//acquire lock
		listenerWaiting++;	//increment waiting listenesr
		if(speakerWaiting > 0 && listenerActive < 1 && speakerActive < 1 ){//wake up a speaker if there is one and thers no active listeners or speakers
			toSpeak.wake();
		}
		if(!messageActive){ 	//listener will sleep if theres no message to pick up
			toListen.sleep();
		}
		listenerWaiting--;		//decrement the listeners waiting
		listenerActive++;		//increment the actives listeners
		ourLock.release();
		
		int returnMessage = message; //moved to a local variable so we can return 
		
		ourLock.acquire();
		messageActive = false;		//message got picked up
		listenerActive--;			//decrement waiting listeners
		if(speakerWaiting>0){
			toSpeak.wake();			//wake a speaker if there currently is one waiting
		}
		ourLock.release();
		
		return returnMessage;		//return the message

    }
	private static class testSpeaker implements Runnable{ //
		private Communicator ourCom;
		private int message;
		private String name;
		
		public testSpeaker(Communicator comm, int msg, String name){
			ourCom = comm;
			message = msg;
			this.name = name;
		}
		public void run(){
			Lib.debug('u',"\n"+name+" Speaking message:"+message);
			ourCom.speak(message);
		}
	}
	private static class testListener implements Runnable{
		private Communicator ourCom;
		private String name;
	
		public testListener(Communicator comm, String name){
			ourCom = comm;
			this.name = name;
		}
		public void run(){
			Lib.debug('u', "\n"+name+" Listening for message");
			Lib.debug('u',"\n"+name+" heard message:"+ourCom.listen());
		}
		
	}
	public static void selfTest(){
		Lib.debug('u',"\nBegin Communicator selfTest");
		Communicator ourCom = new Communicator(); //Communicator for our testcase
		Lib.debug('u',"\nTest 1 : Queued a Speaker with message = 1 then a Listener");
		KThread t1 = new KThread(new testSpeaker(ourCom,1,"SpeakerThread1")); 
		t1.setName("SpeakerThread1");
		t1.fork();
		KThread t2 = new KThread(new testListener(ourCom,"ListenerThread1"));
		t2.setName("ListenerThread1");		
		t2.fork();
		t1.join();//queueing the speaker
		t2.join();//queueing the listener
		Lib.debug('u',"\nTest 1 end\nTest 2 : Queued a Listener then a Speaker with a message = 2");
		KThread t3 = new KThread(new testListener(ourCom,"ListenerThread2"));
		t3.setName("ListenerThread2");		
		t3.fork();
		KThread t4 = new KThread(new testSpeaker(ourCom,2,"SpeakerThread2"));
		t4.setName("SpeakerThread2");		
		t4.fork();
		t3.join();//queueing the listener
		t4.join();//queueing the speaker
		Lib.debug('u',"\nTest 2 end\nTest 3: Queued two Speakers with message = 3 and 4, then two Listeners");
		KThread t5 = new KThread(new testSpeaker(ourCom,3,"SpeakerThread3"));
		KThread t6 = new KThread(new testSpeaker(ourCom,4,"SpeakerThread4"));
		t5.setName("SpeakerThread3");		
		t5.fork();
		t6.setName("SpeakerThread4");		
		t6.fork();
		KThread t7 = new KThread(new testListener(ourCom,"ListenerThread3"));
		KThread t8 = new KThread(new testListener(ourCom,"ListenerThread4"));
		t7.setName("ListenerThread3");		
		t7.fork();
		t8.setName("ListenerThread4");		
		t8.fork();
		t5.join();//queueing the speaker #1
		t6.join();//queueing the speaker #2
		t7.join();//queueing the listener #1
		t8.join();//queueing the listener #2
		Lib.debug('u',"\nTest 3 end\nTest 4: Queued two Listeners then two Speakers  with message = 5 and 6");
		KThread t9 = new KThread(new testListener(ourCom,"ListenerThread5"));
		KThread t10 = new KThread(new testListener(ourCom,"LestenerThread6"));
		t9.setName("ListenerThread5");		
		t9.fork();
		t10.setName("ListenerThread6");		
		t10.fork();
		KThread t11 = new KThread(new testSpeaker(ourCom,5,"SpeakerThread5"));
		KThread t12 = new KThread(new testSpeaker(ourCom,6,"SpeakerThread6"));
		t11.setName("SpeakerThread5");		
		t11.fork();
		t12.setName("SpeakerThread6");		
		t12.fork();
		t9.join(); //queueing the listener #1
		t10.join();//queueing the listener #2
		t11.join();//queueing the speaker #1
		t12.join();//queueing the speaker #2
		Lib.debug('u',"\nTest 4 end\nCommunicator selfTest end");
	}
}
