package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Arrays;
/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {

        myPID = nachosPID++;

        int numPhysPages = Machine.processor().getNumPhysPages();

        frameTable = new boolean[numPhysPages/stackPages];
        pageTable = new TranslationEntry[stackPages];

        System.out.println(frameTable.length);

        // Allocate the page table
        int frame;
        for(frame = 0; frame < frameTable.length; ++frame){if(!frameTable[frame]){break;}}

        Lib.assertTrue(frame < frameTable.length);

        frameTable[frame] = true;

        for (int i=0; i<stackPages; i++)
            pageTable[i] = new TranslationEntry(i,i+(8*frame), true,false,false,false);
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return  a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param   name    the name of the file containing the executable.
     * @param   args    the arguments to pass to the executable.
     * @return  <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        new UThread(this).setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param   vaddr   the starting virtual address of the null-terminated
     *                  string.
     * @param   maxLength       the maximum number of characters in the string,
     *                          not including the null terminator.
     * @return  the string read, or <tt>null</tt> if no null terminator was
     *          found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);
        byte[] bytes = new byte[maxLength+1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length=0; length<bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param   vaddr   the first byte of virtual memory to read.
     * @param   data    the array where the data will be stored.
     * @return  the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param   vaddr   the first byte of virtual memory to read.
     * @param   data    the array where the data will be stored.
     * @param   offset  the first byte to write in the array.
     * @param   length  the number of bytes to transfer from virtual memory to
     *                  the array.
     * @return  the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

        //assure vaddr is within bounds
        if((vaddr < 0) || (vaddr >= pageSize*stackPages)){return 0;}

        byte[] memory = Machine.processor().getMemory();


        int bytesCopied = 0;

        //trim amount of bytes to copy, if length would cuse an out of bounds read
        int copyLength;
        if(length+vaddr > pageSize*stackPages){
            copyLength = (pageSize*stackPages)-vaddr;
        }else{
            copyLength = length;
        }


        while(bytesCopied < copyLength){
            //amount to copy fom the page on the current iteration
            //offsets based on the vaddr if this is the first iteration
            //will not read entire page if there is less left to read than the size of the page
            int amount;
            if(bytesCopied == 0){
                    amount = pageSize - (vaddr % pageSize);
            }else{
                    amount = Math.min(pageSize, (copyLength - bytesCopied));
            }

            //determine which page to read from this iteration
            int page = (vaddr + bytesCopied) / pageSize;


            //if somehow we have reach out of bounds, terminate read here
            if(page >= stackPages){return bytesCopied;}

            //physical address of the page to read from
            int paddr = pageTable[page].ppn * pageSize;

            //if this is the first iteration, offset based on the vaddr
            if(bytesCopied == 0){
                    paddr += vaddr % pageSize;
            }

            //copy over the data and increment the bytesCopied
            System.arraycopy(memory, paddr, data, (offset + bytesCopied), amount);
            bytesCopied += amount;
        }

        return bytesCopied;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param   vaddr   the first byte of virtual memory to write.
     * @param   data    the array containing the data to transfer.
     * @return  the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param   vaddr   the first byte of virtual memory to write.
     * @param   data    the array containing the data to transfer.
     * @param   offset  the first byte to transfer from the array.
     * @param   length  the number of bytes to transfer from the array to
     *                  virtual memory.
     * @return  the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

        //assure vaddr is within bounds
        if((vaddr < 0) || (vaddr >= pageSize*stackPages))
            return 0;

        byte[] memory = Machine.processor().getMemory();

        //trim amount of bytes to copy, if length would cuse an out of bounds read
        int copyLength;
        if(length+vaddr > pageSize*stackPages){
            copyLength = (pageSize*stackPages)-vaddr;
        }else{
            copyLength = length;
        }

        int bytesCopied = 0;
        while(bytesCopied < copyLength){
            //amount to copy fom the page on the current iteration
            //offsets based on the vaddr if this is the first iteration
            //will not write to entire page if there isn't enough to write
            int amount;
            if(bytesCopied == 0){
                    amount = pageSize - (vaddr % pageSize);
            }else{
                    amount = Math.min(pageSize, (copyLength - bytesCopied));
            }

            //determine which page to read from this iteration
            int page = (vaddr + bytesCopied) / pageSize;


            //if somehow we have reach out of bounds, terminate read here
            if(page >= stackPages){return bytesCopied;}

            //physical address of the page to read from
            int paddr = pageTable[page].ppn * pageSize;

            //if this is the first iteration, offset based on the vaddr
            if(bytesCopied == 0){
                    paddr += vaddr % pageSize;
            }

            //copy over the data and increment the bytesCopied
            System.arraycopy(data, (offset + bytesCopied), memory, (paddr + bytesCopied), amount);
            bytesCopied += amount;
        }

        return bytesCopied;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param   name    the name of the file containing the executable.
     * @param   args    the arguments to pass to the executable.
     * @return  <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i=0; i<args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages*pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages-1)*pageSize;
        int stringOffset = entryOffset + args.length*4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i=0; i<argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                       argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return  <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }

        // load sections
        for (int s=0; s<coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess, "\tinitializing " + section.getName()
                      + " section (" + section.getLength() + " pages)");

            for (int i=0; i<section.getLength(); i++) {
                int vpn = section.getFirstVPN()+i;

                // for now, just assume virtual addresses=physical addresses
                section.loadPage(i, vpn);
            }
        }
        return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

        // by default, everything's 0
        for (int i=0; i<processor.numUserRegisters; i++)
            processor.writeRegister(i, 0);

        // initialize PC and SP according
        processor.writeRegister(Processor.regPC, initialPC);
        processor.writeRegister(Processor.regSP, initialSP);

        // initialize the first two argument registers to argc and argv
        processor.writeRegister(Processor.regA0, argc);
        processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        if(KThread.currentThread() instanceof UThread)
                return -1;


        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }


    private static final int
        syscallHalt = 0,
        syscallExit = 1,
        syscallExec = 2,
        syscallJoin = 3,
        syscallCreate = 4,
        syscallOpen = 5,
        syscallRead = 6,
        syscallWrite = 7,
        syscallClose = 8,
        syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     *                                                          </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *                                                          </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *                                                          </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param   syscall the syscall number.
     * @param   a0      the first syscall argument.
     * @param   a1      the second syscall argument.
     * @param   a2      the third syscall argument.
     * @param   a3      the fourth syscall argument.
     * @return  the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
        case syscallHalt:
            return handleHalt();

        case syscallExit:
            return handleExit(a0);

        case syscallExec:
            return handleExec(a0,a1,a2);

        case syscallJoin:
            return handleJoin(a0,a1);

        case syscallCreate:
            return handleCreat(a0);

        case syscallOpen:
            return handleOpen(a0);

        case syscallRead:
            return handleRead(a0,a1,a2);

        case syscallWrite:
            return handleWrite(a0,a1,a2);

        case syscallClose:
            return handleClose(a0);

        case syscallUnlink:
            return handleUnlink(a0);


        default:
            Lib.debug(dbgProcess, "Unknown syscall " + syscall);
            Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }


    private int handleCreat(int filename){

        //fetch the name
        byte[] byteString = new byte[256];
        readVirtualMemory(filename, byteString, 0, 256);

        //find the null terminator
        int i;
        for(i = 0; byteString[i] !=0 ; i++);

        //conver the name into a string
        byte[] temp = new byte[i];
        System.arraycopy(byteString, 0, temp, 0, i);
        String read = new String(temp);

        //check the file table for the give file
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != read; index++);
        if(index < 16){
            //if the file is already in the file table, it already exists, no need to create a new one
            //so, just  increment the conter for how many processes are editing/reading this file
            processCount[index]++;
            return index;
        }

        //if the file is not in the file table, find an open slot in the file table, and put it there
        for(index = 0; index < 16 && openFiles[index] != null; index++);

        //if there is no open slots in the file table, then return -1 to indicate that it is done
        if(index >= 16)
            return -1;

        //the open slot will be given the file if it exists, or a new file if it didn't already exist
        openFiles[index] = Machine.stubFileSystem().open(read,true);
        processCount[index]++;
        return index;
    }

    private int handleOpen(int filename){

        //fetching the filename, cutting it to size, turning it into a string
        byte[] byteString = new byte[256];
        readVirtualMemory(filename,byteString,0,256);

        int i;
        for(i = 0; byteString[i] != 0; i++);

        byte[] temp = new byte[i];
        System.arraycopy(byteString, 0, temp, 0, i);
        String read = new String(temp);

        //check for it in the file table, if present, increment the counter for that file, and return
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != read; index++);
        if(index < 16){
            processCount[index]++;
            return index;
        }

        //otherwise if it  isn't add it to the table (if the file exists)
        for(index = 0; index < 16 && openFiles[index] != null; index++);
        //return -1 if there is no space in the filetable
        if(index >=16)
            return -1;

        openFiles[index] = Machine.stubFileSystem().open(read,false);
        processCount[index]++;
        return index;
    }

    private int handleRead(int fd, int buffer, int size){

        //fetching, cutting to size, and converting to string the filename
        byte[] byteString = new byte[256];
        readVirtualMemory(fd, byteString, 0,  256);

        int i;
        for(i=0;byteString[i]!=0;i++);

        byte[] temp = new byte[i];
        System.arraycopy(byteString,0,temp,0,i);
        String fileName = new String(temp);


        //check for it in the file table, if present, increment the counter for that file, and return
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != fileName; index++);
        //if it isn't open, return -1 to indicate error
        if(index >=16)
            return -1;

        //read the bytestring from file
        byte[] tempBuffer = new byte[size];
        int bytesRead = openFiles[index].read(0,tempBuffer,0,size);

        //write it to the buffer for the calling process
        writeVirtualMemory(buffer,tempBuffer,0,size);
        return bytesRead;
    }

    private int handleWrite(int fd,int buffer,int size){

        ///fetching, cutting to size, and converting to string the filename
        byte[] byteString = new byte[256];
        readVirtualMemory(fd, byteString, 0,  256);

        int i;
        for(i=0;byteString[i]!=0;i++);

        byte[] temp = new byte[i];
        System.arraycopy(byteString,0,temp,0,i);
        String fileName = new String(temp);


        //check for it in the file table, if present, increment the counter for that file, and return
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != fileName; index++);
        //if it isn't open, return -1 to indicate error
        if(index >=16)
            return -1;

        //fetch the bytestring to be written to file
        byte[] tempBuffer = new byte[size];
        readVirtualMemory(buffer,tempBuffer,0,size);

        //write the ytestring to file
        int bytesWritten = openFiles[index].write(0,tempBuffer,0,size);
        return bytesWritten;
    }


    private int handleClose(int fd){
        //fetching, cutting to size, and converting to string, the filename
        byte[] byteString = new byte[256];
        readVirtualMemory(fd, byteString, 0,  256);
        int i;
        for(i=0;byteString[i]!=0;i++);
        byte[] temp = new byte[i];
        System.arraycopy(byteString,0,temp,0,i);
        String toClose = new String(temp);

        //find the index that the file is sitting at in the file table
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != toClose; index++);


        if(index < 16){
            //if found decrement the conter for number of precesses using this file and, if no processes still using it,
            //remove it from the file table
            processCount[index]--;
            if(processCount[index]<=0){
                openFiles[index].close();
                openFiles[index] = null;
                //if it was marked for deletion and is to be removed from the file table, delete it as well
                if(awaitingDeletion[index]){handleUnlink(fd);}
            }
            return 0;
        }else{
            //file is already not in the file table
            return -1;
        }

    }


private int handleUnlink(int filename){

        //fetching, cutting to size, and converting to string, the filename
        byte[] byteString = new byte[256];
        readVirtualMemory(filename, byteString, 0,  256);

        int i;
        for(i=0;byteString[i]!=0;i++);

        byte[] temp = new byte[i];
        System.arraycopy(byteString,0,temp,0,i);
        String toUnlink = new String(temp);

        //find the index of it in the file table
        int index;
        for(index = 0; index < 16 && openFiles[index].getName() != toUnlink; index++);
        if(index < 16){
            //if in the file table decrement the counter for number of processes using this file
            processCount[index] --;
            if(processCount[index] > 0){
                //if there are still processes using this file, mark it for deletion and return
                awaitingDeletion[index] = true;
                return 1;
            }
            //otherwise, remove it from the file table
            awaitingDeletion[index] = false;
            openFiles[index] = null;
        }

        //finally, delete the file
        boolean success = Machine.stubFileSystem().remove(toUnlink);
        if(success){
            return 0;
        }else{
            return 1;
        }
    }

    private int handleExec(int filenameAddr, int argc, int argvAddr){

        int[] argPtrs = new int[argc];

        for (int i=0; i < argc; i++ ){
            byte[] temp = new byte[4];
            readVirtualMemory(argvAddr+(i*4),temp,0,4);
            argPtrs[i] = Lib.bytesToInt(temp,0);
        }

        String[] args = new String[argc];


        for (int j=0; j<argc; j++){
            byte[] byteString = new byte[256];
            readVirtualMemory(argPtrs[j], byteString, 0,  256);

            int i;
            for(i = 0; byteString[i] != 0;  i++);

            byte[] temp = new byte[i];
            System.arraycopy(byteString,0,byteString, 0, i);

            args[j] = new String(temp);
        }


        byte[] byteString = new byte[256];
        readVirtualMemory(filenameAddr, byteString, 0,  256);
        int i;
        for(i = 0; byteString[i] != 0;  i++);
        byte[] temp = new byte[i];
        System.arraycopy(byteString,0,byteString, 0, i);

        String filename = new String(temp);

        execute(filename, args);

        return 0;
    }

    private int handleJoin(int pidToJoin, int statusAddr){
        if (isParent( pidToJoin, myPID )){

            //call .join() on the uThread of the childPID
            UserProcess childProcess = findProcess(pidToJoin);

            if(childProcess != null) {
                childProcess.thisUThread.join();

                byte[] temp = new byte[4];
                childProcess.readVirtualMemory(0,temp,0,4);
                return Lib.bytesToInt(temp,0);
            }

        }

        return -1;
    }

    private int handleExit(int status){

        writeVirtualMemory(0,Lib.bytesFromInt(status),0,0);
        unloadSections();
        thisUThread.finish();

        boolean noLivingProcesses = true;
        for(int i =0; i<frameTable.length;i++){
            if(frameTable[i] = true)
                noLivingProcesses = false;
        }
        if(noLivingProcesses){//there are no living processes
            Machine.halt();
        }

        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param   cause   the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();
        switch (cause) {
            case Processor.exceptionSyscall:
            int result = handleSyscall(processor.readRegister(Processor.regV0),
                processor.readRegister(Processor.regA0),
                processor.readRegister(Processor.regA1),
                processor.readRegister(Processor.regA2),
                processor.readRegister(Processor.regA3));

            processor.writeRegister(Processor.regV0, result);
            processor.advancePC();
            break;
                default:
                    Lib.debug(dbgProcess, "Unexpected exception: " +
                    Processor.exceptionNames[cause]);
                    Lib.assertNotReached("Unexpected exception");
        }
    }


    public UserProcess findProcess(int PID){
            for (int i = 0; i <= processes.length ; i++){
                    if (myPID == PID){
                            return processes[i];
                    }
            }
            return null;
    }

    //This Function will search through pidParents array and check return true if the parent
    //passed as argument is the actual parent of the child passed as argument.
    public boolean isParent (int childPID, int parentPID){
        for (int i=0; i <= pidParents.length; i++){
            if(childPID == pidParents[i][0] && parentPID == pidParents[i][1]){
                return true;
            }
        }
        return false;
    }

    static OpenFile[] openFiles = new OpenFile[16];        //an array of files being worked on
    static boolean[] awaitingDeletion = new boolean[16]; //an array of booleans describing if the file is awaiting deletion
    static int[] processCount = new int[16];    //an array of the amount of processes that have that file open

    static boolean[] frameTable;//the frame table
    private int myFrame;//this process' frame index

    int myPID; // PID of current process
    public UThread thisUThread; // UThread that belongs to current process
    static UserProcess[] processes = new UserProcess[16];  // Array of userProcesses
    static int[][] pidParents = new int[17][2]; // 2d array, to store pids and their parents pid
    static int nachosPID = 1; // always a non negative integer, all pids should be unique, increment when new pid is assigned.

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
}
