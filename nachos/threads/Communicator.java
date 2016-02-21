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
		
		public testSpeaker(Communicator comm, int msg){
			ourCom = comm;
			message = msg;
		}
		public void run(){
			Lib.debug('u',"\nCurrently Speaking a message:"+message);
			ourCom.speak(message);
		}
	}
	private static class testListener implements Runnable{
		private Communicator ourCom;
		
		public testListener(Communicator comm){
			ourCom = comm;
		}
		public void run(){
			Lib.debug('u',"\nCurrently Listening to a message:"+ourCom.listen());
		}
		
	}
	public static void selfTest(){
		Communicator ourCom = new Communicator(); //Communicator for our testcase
		Lib.debug('u',"\nAttempt 1 : Queued a Speaker with message = 1 then a Listener");
		KThread t1 = new KThread(new testSpeaker(ourCom,1)); 
		t1.setName("CommunicatorThread1");
		t1.fork();
		KThread t2 = new KThread(new testListener(ourCom));
		t2.setName("CommunicatorThread2");		
		t2.fork();
		t1.join();//queueing the speaker
		t2.join();//queueing the listener
		Lib.debug('u',"\nAttempt 2 : Queued a Listener then a Speaker with a message = 2");
		KThread t3 = new KThread(new testListener(ourCom));
		t3.setName("CommunicatorThread3");		
		t3.fork();
		KThread t4 = new KThread(new testSpeaker(ourCom,2));
		t4.setName("CommunicatorThread4");		
		t4.fork();
		t3.join();//queueing the listener
		t4.join();//queueing the speaker
		Lib.debug('u',"\nAttempt 3: Queued two Speakers with message = 3 and 4, then two Listeners");
		KThread t5 = new KThread(new testSpeaker(ourCom,3));
		KThread t6 = new KThread(new testSpeaker(ourCom,4));
		t5.setName("CommunicatorThread5");		
		t5.fork();
		t6.setName("CommunicatorThread6");		
		t6.fork();
		KThread t7 = new KThread(new testListener(ourCom));
		KThread t8 = new KThread(new testListener(ourCom));
		t7.setName("CommunicatorThread7");		
		t7.fork();
		t8.setName("CommunicatorThread8");		
		t8.fork();
		t5.join();//queueing the speaker #1
		t6.join();//queueing the speaker #2
		t7.join();//queueing the listener #1
		t8.join();//queueing the listener #2
		Lib.debug('u',"\nAttempt 4:: Queued two Listeners then two Speakers  with message = 5 and 6");
		KThread t9 = new KThread(new testListener(ourCom));
		KThread t10 = new KThread(new testListener(ourCom));
		t9.setName("CommunicatorThread9");		
		t9.fork();
		t10.setName("CommunicatorThread10");		
		t10.fork();
		KThread t11 = new KThread(new testSpeaker(ourCom,5));
		KThread t12 = new KThread(new testSpeaker(ourCom,6));
		t11.setName("CommunicatorThread11");		
		t11.fork();
		t12.setName("CommunicatorThread12");		
		t12.fork();
		t9.join(); //queueing the listener #1
		t10.join();//queueing the listener #2
		t11.join();//queueing the speaker #1
		t12.join();//queueing the speaker #2
	}
}
