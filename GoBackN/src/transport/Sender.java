package transport;

public class Sender extends NetworkHost {

    /*
     * Predefined Constant (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and Packet payload
     *
     *
     * Predefined Member Methods:
     *
     *  void startTimer(double increment):
     *       Starts a timer, which will expire in "increment" time units, causing the interrupt handler to be called.  You should only call this in the Sender class.
     *  void stopTimer():
     *       Stops the timer. You should only call this in the Sender class.
     *  void udtSend(Packet p)
     *       Sends the packet "p" into the network to arrive at other host
     *  void deliverData(String dataSent)
     *       Passes "dataSent" up to app layer. You should only call this in the Receiver class.
     *
     *  Predefined Classes:
     *
     *  NetworkSimulator: Implements the core functionality of the simulator
     *
     *  double getTime()
     *       Returns the current time in the simulator. Might be useful for debugging. Call it as follows: NetworkSimulator.getInstance().getTime()
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for debugging. Call it as follows: NetworkSimulator.getInstance().printEventList()
     *
     *  Message: Used to encapsulate a message coming from the application layer
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      void setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *      String getData():
     *          returns the data contained in the message
     *
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet, which is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload):
     *          creates a new Packet with a sequence field of "seq", an ack field of "ack", a checksum field of "check", and a payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an ack field of "ack", a checksum field of "check", and an empty payload
     *    Methods:
     *      void setSeqnum(int seqnum)
     *          sets the Packet's sequence field to seqnum
     *      void setAcknum(int acknum)
     *          sets the Packet's ack field to acknum
     *      void setChecksum(int checksum)
     *          sets the Packet's checksum to checksum
     *      void setPayload(String payload) 
     *          sets the Packet's payload to payload
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      String getPayload()
     *          returns the Packet's payload
     *
     */
    
    // Add any necessary class variables here. They can hold state information for the sender. 
    // Also add any necessary methods (e.g. checksum of a String)
    // current sequence number / state in FSM
    private int nxtSeqNum;
    private Packet[] sndpkt;
    private int base;
    private static final int windowSize = 8;
    private static final int increment = 40;
    private static final int maxBuffered = 50;
    private int msgCount;
    private Message[] buffer;
    
    
    // check incoming packet to see if corrupted
    private boolean check(Packet p){
        // hints gives explaination for checksum
        // should be same both sender and receiver side
        int sum = p.getAcknum() + p.getChecksum() + p.getSeqnum();
        // should be -1 or 111111 etc in binary due to the first digit being negative
        return sum == -1;
    }
    
    // gets a checksum from sequence number and acknowledgement number
    // adds the two together gets the ones complement of the answer and converts this to integer
    private int getChecksum(int sNum, int aNum, String data){
        
        int sum = 0;
        for (int i = 0; i < data.length(); i++) {
            sum += (int) data.charAt(i);
        }
        sum += aNum + sNum;
        // the ~ here does ones complement on the sum
        return Integer.parseUnsignedInt(Integer.toBinaryString(~(sum)), 2);
    }    
    
    // used to send without increasing the message count
    // called when window size is free and more messages are in the queue
    // or when output is called
    private void out(Message message){
        if(nxtSeqNum < base+windowSize){
            sndpkt[nxtSeqNum - base] = new Packet(nxtSeqNum, 0, getChecksum(nxtSeqNum, 0, message.getData()), message.getData());
            udtSend(sndpkt[nxtSeqNum - base]);
            if(base == nxtSeqNum){
                startTimer(increment);
            }
            nxtSeqNum++;
        }
    }
    
    // This is the constructor.  Don't touch!
    public Sender(int entityName) {
        super(entityName);
    }

    // This method will be called once, before any of your other sender-side methods are called. 
    // It can be used to do any required initialisation (e.g. of member variables you add to control the state of the sender).
    @Override
    public void init() {
        nxtSeqNum = 0;
        base = 0;
        msgCount = 0;
        sndpkt = new Packet[windowSize];
        buffer = new Message[maxBuffered];
    }
    
    // This method will be called whenever the app layer at the sender has a message to send.  
    // The job of your protocol is to ensure that the data in such a message is delivered in-order, and correctly, to the receiving application layer.
    @Override
    public void output(Message message) {
        buffer[msgCount % maxBuffered] = message;
        msgCount++;
        out(message);

    }
    
    // This method will be called whenever a packet sent from the receiver (i.e. as a result of a udtSend() being done by a receiver procedure) arrives at the sender.  
    // "packet" is the (possibly corrupted) packet sent from the receiver.
    @Override
    public void input(Packet packet) {
            boolean corrupt = !check(packet);
            if (!corrupt && packet.getAcknum() == 0){
                Packet[] tempPacket = new Packet[windowSize];
                // we copy the array into a temp array in order to get rid of redunant packets
                System.arraycopy(sndpkt, (packet.getSeqnum()+1)-base, tempPacket, 0, windowSize - ((packet.getSeqnum()+1)-base));
                sndpkt = tempPacket;
                base = packet.getSeqnum()+ 1;
                if (base == nxtSeqNum){
                    stopTimer();
                }
                else{
                    // if more messages to send
                    if(nxtSeqNum<msgCount){
                        out(buffer[nxtSeqNum%maxBuffered]);
                    }
                    startTimer(increment);
                } 
            }
 
    }
    
    // This method will be called when the senders's timer expires (thus generating a timer interrupt). 
    // You'll probably want to use this method to control the retransmission of packets. 
    // See startTimer() and stopTimer(), above, for how the timer is started and stopped. 
    @Override
    public void timerInterrupt() {
        startTimer(increment);
        for (int i = 0; i < (nxtSeqNum-base); i++){
            udtSend(sndpkt[i]);
        }
    }
}
