package nachos.threads;

import nachos.machine.*;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        sleepingThreads = new PriorityBlockingQueue<SleepingThread>();
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() { timerInterrupt(); }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        KThread.currentThread().yield();
        for(int i = 0; i < sleepingThreads.size(); ++i){
            SleepingThread s = sleepingThreads.peek();
            if(Machine.timer().getTime() > s.wakeTime){
                s.thread.ready();
                sleepingThreads.poll();
            }else{
              // Priorty queue only has threads that do not need to be woken up.
              break;
            }
        }
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) &gt;= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param   x       the minimum number of clock ticks to wait.
     *
     * @see     nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        if (x <= 0) return;

        sleepingThreads.add(new SleepingThread(KThread.currentThread(), Machine.timer().getTime() + x));
        Machine.interrupt().disable();
        KThread.sleep();
        Machine.interrupt().enable();
    }

    private Queue<SleepingThread> sleepingThreads = null;

    private class SleepingThread implements Comparable<SleepingThread> {
        public KThread thread;
        public long wakeTime;

        public SleepingThread(KThread k, long t) {
            thread = k;
            wakeTime = t;
        }

        public int compareTo(SleepingThread other){
            return (int)(this.wakeTime - other.wakeTime);
        }

        @Override
        public String toString(){
            return "SleepingThread: [" + thread + ", " + wakeTime  + "]";
        }
    }

    private static class AlarmTestThread implements Runnable {
        private long wakeTime;
        private KThread dependsOn;

        public AlarmTestThread(long wakeTime, KThread dependsOn) {
            this.wakeTime = wakeTime;
            this.dependsOn = dependsOn;
        }

        public void run() {
            String name = KThread.currentThread().getName();
            Lib.debug('v', Machine.timer().getTime() + ": " + name + " is starting execution.");
            if(dependsOn != null){
                Lib.debug('v', Machine.timer().getTime() + ": " + name + " is waiting for " + dependsOn + ".");
                dependsOn.join();
            }
            if(Machine.timer().getTime() < wakeTime){
                Lib.debug('v', Machine.timer().getTime() + ": " + name + " is sleeping until " + wakeTime + ".");
                ThreadedKernel.alarm.waitUntil(wakeTime);
                Lib.debug('v', Machine.timer().getTime() + ": " + name + " woke up.");
            } else {
                Lib.debug('v', Machine.timer().getTime() + ": " + name + " doesn't need to sleep.");
            }
            Lib.debug('v', Machine.timer().getTime() + ": " + name + " is finishing execution.");
            KThread.finish();
        }
    }

    /**
     * Self test for Alarm.
     */
    public static void selfTest(){
        Lib.debug('v',"\n---- Entering selfTest() for Alarm.class ---------------------------------------");

        /*
         * All threads output their debug data with the time of the current print, as Alarms are time-sensitive.
         */

        /*
         * Test 1 tests the wake order and times for threads that use alarms.
         */
        Lib.debug('v',"\n-- Test 1: Assure that threads wake in order. ----------------------------------");
        Lib.debug('v',"-- Alarm Thread 1 should awaken 10000 ticks after Alarm Thread 2. ---------------");
        KThread thread1 = new KThread(new AlarmTestThread(20000, null));
        thread1.setName("Alarm Thread 1");
        KThread thread2 = new KThread(new AlarmTestThread(10000, null));
        thread2.setName("Alarm Thread 2");
        thread1.fork();
        thread2.fork();
        thread1.join();
        thread2.join();

        /*
         * Test 2 tests if the wake time is valid I.E has yet to pass.
         */
        Lib.debug('v',"\n-- Test 2: Assure that a thread will wake up if told to wait for a past time. --");
        Lib.debug('v',"-- Alarm Thread 3 should not sleep. --------------------------------------------");
        KThread thread3 = new KThread(new AlarmTestThread(-5000, null));
        thread3.setName("Alarm Thread 3");
        thread3.fork();
        thread3.join();

        /*
         * Test 3 tests joins to make sure that a joining thread will wait for another to be awoken by the Alarm.
         */
        Lib.debug('v',"\n-- Test 3: Assure that a thread will wait for a sleeping thread. ---------------");
        Lib.debug('v',"-- Alarm Thread 5 will wait until Alarm Thread 4 wakes up. ---------------------");
        KThread thread4 = new KThread(new AlarmTestThread(10000, null));
        thread4.setName("Alarm Thread 4");
        // Have thread5 joining thread4, which will sleep.
        // Thread5 will wait for thread4 to wake.
        KThread thread5 = new KThread(new AlarmTestThread(0, thread4));
        thread5.setName("Alarm Thread 5");
        thread4.fork();
        thread5.fork();
        thread4.join();
        thread5.join();

        Lib.debug('v',"\n---- Exiting selfTest() for Alarm.class ----------------------------------------\n");
    }
}
