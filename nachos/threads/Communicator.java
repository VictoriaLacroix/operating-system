package nachos.threads;

import nachos.machine.*;

public class Communicator{
	
	private int message;
	
	Lock ourLock;
	Condition2 toSpeak;
	Condition2 toListen;
	int listenerWaiting = 0;
	int speakerWaiting = 0;
	int speakerActive = 0;
	int listenerActive = 0;
	boolean messageActive = false;
	
	public Communicator(){
		ourLock = new Lock();
		toSpeak = new Condition2(ourLock);
		toListen = new Condition2(ourLock);
	}


	public void speaker(int word){
		ourLock.acquire();	
		speakerWaiting++;
		
		while(listenerWaiting==0||speakerActive > 0 || listenerActive > 0 || messageActive){
			toSpeak.sleep();
		}
		speakerWaiting--;	
		speakerActive++;
		ourLock.release();
		
		message = word;		//Transfer word to message
		
		ourLock.acquire();	//Reacquire ourLock to decrement the active speakers and have an activeMessage
		messageActive = true;
		speakerActive--;
		toListen.wake();	//Wake up a listener so it can listen to the word
		ourLock.release();
	}
	
	public int listener(){
		ourLock.acquire();
		listenerWaiting++;
		while(speakerWaiting > 0 && listenerActive < 1 && speakerActive < 1 ){//wake up a speaker if there is one
			toSpeak.wake();
		}
		if(!messageActive){ 	//listener will sleep if theres no message to pick up
			toListen.sleep();
		}
		listenerWaiting--;
		listenerActive++;
		ourLock.release();
		
		int returnMessage = message; //moved to a local variable so we can return 
		
		ourLock.acquire();
		messageActive = false;		//message got picked up
		listenerActive--;
		if(speakerWaiting>0){
			toSpeak.wake();
		}
		ourLock.release();
		
		return returnMessage;
	}


}