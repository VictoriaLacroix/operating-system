package nachos.threads;
import nachos.machine.*;
import java.util.ArrayDeque;
public class MakeWater {
	private Condition2 H , O;
	private Lock waterLock;
	private int hCount, oCount;

	public MakeWater(){
		waterLock = new Lock();
		H = new Condition2(waterLock);
		O = new Condition2(waterLock);
		hCount = 0;
		oCount = 0;
	}


	public void hReady(){


		waterLock.acquire();
		boolean intStatus = Machine.interrupt().disable();

		hCount++;

		if((hCount<2) || (oCount<1)){
			H.sleep();
		}else{
			H.wake();
			O.wake();
			makeWater();
		}
		Machine.interrupt().restore(intStatus);	
		waterLock.release();
	}

	public void oReady(){
		waterLock.acquire();
		boolean intStatus = Machine.interrupt().disable();

		oCount++;

		if((hCount<2) || (oCount<1)){
			O.sleep();
		}else{
			H.wake();
			H.wake();
			makeWater();
		}

		Machine.interrupt().restore(intStatus);	
		waterLock.release();
	}
	
	private void makeWater(){
		hCount -= 2;
		oCount--;
		System.out.println("Water is made");

	}

	private static class TestThread implements Runnable{

		private MakeWater maker;
		private String name;
		private boolean isOxygen;
		public TestThread(MakeWater maker, String name, boolean isOxygen){
			this.maker = maker;
			this.name = name;
			this.isOxygen = isOxygen;
		}

		public void run(){
			Lib.debug('w',"\n"+name+" Started");

			if(isOxygen){
				maker.oReady();		
			}else{
				maker.hReady();
			}

			Lib.debug('w',"\n"+name+" Finished");
			KThread.finish();
		}

	}


	public static void selfTest(){
		Lib.debug('w',"\nBegin MakeWater selfTest");

		MakeWater maker = new MakeWater();

		KThread HT1,HT2,HT3,HT4,HT5,HT6,HT7,HT8,OT1,OT2,OT3,OT4;


		Lib.debug('w',"\nMakeWater test 1 : queue 2 H then 1 O");
		HT1 = new KThread(new TestThread(maker, "Hydrogen Thread 1",false));
		HT2 = new KThread(new TestThread(maker, "Hydrogen Thread 2",false));
		OT1 = new KThread(new TestThread(maker, "Oxygen Thread 1",true));
	
		HT1.fork();
		HT2.fork();
		OT1.fork();

		//KThread.yield();
		HT1.join();
		HT2.join();
		OT1.join();

		Lib.debug('w',"\nMakeWater test1 finished");


		Lib.debug('w',"\nMakeWater test2 : queue an O and then 2 H");

		HT3 = new KThread(new TestThread(maker, "Hydrogen Thread 3",false));
		HT4 = new KThread(new TestThread(maker, "Hydrogen Thread 4",false));
		OT2 = new KThread(new TestThread(maker, "Oxygen Thread 2",true));
	
		OT2.fork();
		HT3.fork();
		HT4.fork();

		//KThread.yield();
		OT2.join();
		HT3.join();
		HT4.join();

		Lib.debug('w',"\nMakeWater test2 finished");
		Lib.debug('w',"\nMakeWater test2 : queue 2 O and then 4 H");

		HT5 = new KThread(new TestThread(maker, "Hydrogen Thread 3",false));
		HT6 = new KThread(new TestThread(maker, "Hydrogen Thread 4",false));
		HT7 = new KThread(new TestThread(maker, "Hydrogen Thread 3",false));
		HT8 = new KThread(new TestThread(maker, "Hydrogen Thread 4",false));
		OT3 = new KThread(new TestThread(maker, "Oxygen Thread 2",true));
		OT4 = new KThread(new TestThread(maker, "Oxygen Thread 2",true));
	
		OT3.fork();
		OT4.fork();
		HT5.fork();
		HT6.fork();
		HT7.fork();
		HT8.fork();

		//KThread.yield();
		OT3.join();
		OT4.join();
		HT5.join();
		HT6.join();
		HT7.join();
		HT8.join();

		Lib.debug('w',"\nMakeWater test2 finished");

		Lib.debug('w',"\nExit MakeWater selfTest");
	}



}
