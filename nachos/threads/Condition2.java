package nachos.threads;

import nachos.machine.*;


/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
    }
    
	
   	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
    private Lock conditionLock;
	private boolean isNext = false; // boolean flag for wakeall(), makes sure something is in queue.
	
	
    public static void selfTest() {
		
		
		Lib.debug('e',"\n\n\n --------------------------------- !Entering Condition2 SelfTest! -------------------------------");
		
		// Tests to see that I can put threads to sleep and they exist on the threadqueue within Condition2
		Lib.debug('e',"\n\n\n ------Tests to see that I can put threads to sleep and they exist on the threadqueue within Condition2-----\n");
		Lock l = new Lock();
    	Condition2 c2 = new Condition2(l);
		
		// create 4 new threads
		KThread thread1 = new KThread (new TestThread(c2, l, "thread1"));
		KThread thread2 = new KThread (new TestThread(c2, l, "thread2"));
		KThread thread3 = new KThread (new TestThread(c2, l, "thread3"));
		KThread thread4 = new KThread (new TestThread(c2, l, "thread4"));
		
		// 2 new threads
		Lib.debug('e',"\n   // 2 new threads  \n");
		thread1.fork();
		thread1.setName("thread1");
		KThread.currentThread().yield();
		
		thread2.fork();
		thread2.setName("thread2");
		KThread.currentThread().yield();
		
		// Continues Tests to make sure wake() function works.
			//wake nextThread
		Lib.debug('e',"\n\n\n ------// Continues Tests to make sure wake() function works.-----\n");
			Lib.debug('e',"\n   //wake nextThread  \n");
		l.acquire();
		c2.wake();
		l.release();
		
		// Continues Tests to make sure wakeall() function works.
		Lib.debug('e',"\n\n\n ------// Continues Tests to make sure wakeall() function works.-----\n");
			
			// 2 new threads
			Lib.debug('e',"\n   // 2 new threads  \n");
			thread3.fork();
			thread3.setName("thread3");
			KThread.currentThread().yield();
			
			thread4.fork();
			thread4.setName("thread4");
			KThread.currentThread().yield();
		
		//wakeAll threads
		Lib.debug('e',"\n   //wakeAll threads  \n");
		l.acquire();
		c2.wakeAll();
		Lib.debug('e'," --------------------------------- !Exiting Condition2 SelfTest! ------------------------------- \n\n\n");
        }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
		
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean iStat = Machine.interrupt().disable();
		
		conditionLock.release();

		if(Communicator.supressDebug == false)
			Lib.debug('e', KThread.currentThread().getName() + " is sleeping now!");
	
		isNext = true; // boolean flag for wakeall(), makes sure something is in queue.
		waitQueue.waitForAccess(KThread.currentThread());
		
		if(Communicator.supressDebug == false){
			Lib.debug('e', "\n Current Sleeping threads: \n" );
			waitQueue.print(); // This code is here for debug purposes, i'd much rather use a Lib.debug, but I'm not rewriting other lc
			Lib.debug('e', "\n" );
		}
		
		KThread.currentThread().sleep();
		
		conditionLock.acquire();
		
		Machine.interrupt().restore(iStat);
	
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
		
		
				Lib.assertTrue(conditionLock.isHeldByCurrentThread());
				boolean iStat = Machine.interrupt().disable();
				
				KThread kthread = waitQueue.nextThread();
				
				if(kthread != null){
					isNext = true; // boolean flag for wakeall(), makes sure something is in queue.
					if(Communicator.supressDebug == false){
						Lib.debug('e',KThread.currentThread().getName() + " is awoken now! \n");
						Lib.debug('e', "Threads still on queue: \n" );
						waitQueue.print();
						Lib.debug('e', "\n" );
					}
					kthread.ready();
				}else{
					isNext = false; // boolean flag for wakeall(), makes sure something is in queue.
				}
				Machine.interrupt().restore(iStat);
				
    }

    
    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock. Uses Wake() function as many times as
	 * there are threads on the queue.
     */
	 
    public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean iStat = Machine.interrupt().disable();
			
			wake();
			
		while(isNext == true){
			wake();
		}
		
		Machine.interrupt().restore(iStat);
	}

	
	
	
		 private static class TestThread implements Runnable{
			 
			 Lock lock;
			 Condition2 c2;
			 String name;
			 public TestThread(Condition2 c2, Lock l, String name ){
				 this.lock = l;
				 this.c2 = c2;
				 this.name = name;
			 }
			 
			public void run(){
				
				
				Lib.debug('e', " \n " + name + " is being put to sleep if it can acquire lock.");
				lock.acquire();
				c2.sleep();
				lock.release();
				Lib.debug('e', name + " is awake.");
				
			}
		}
	 }
	 