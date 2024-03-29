package net.sharksystem.asap;

import net.sharksystem.asap.management.ASAPManagementStorage;
import net.sharksystem.asap.management.ASAPManagementStorageImpl;
import net.sharksystem.asap.protocol.*;
import net.sharksystem.asap.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * That ASAPEngine manages exchange of stored messages with peers.
 * See ASPChunkStorage for details.
 * 
 * @see ASAPStorage
 * @author thsc
 */
public abstract class ASAPEngine implements ASAPStorage, ASAPProtocolEngine, ASAPManagementStorage {
    public static final String ANONYMOUS_OWNER = "anon";
    static String DEFAULT_OWNER = ANONYMOUS_OWNER;
    static int DEFAULT_INIT_ERA = 0;

    protected String owner = ANONYMOUS_OWNER;
    protected String format = ASAP_1_0.ANY_FORMAT.toString();

    protected int era = 0;
    protected int oldestEra = 0;
    protected HashMap<String, Integer> lastSeen = new HashMap<>();
    protected ASAPMemento memento = null;
    
    /* private */ final private ASAPChunkStorage chunkStorage;
    protected boolean dropDeliveredChunks = false;

    private ASAPOnlineMessageSender asapOnlineMessageSender;
    protected boolean contentChanged = false;
    protected boolean sendReceivedChunks = false;

    protected ASAPEngine(ASAPChunkStorage chunkStorage, CharSequence chunkContentFormat)
            throws ASAPException, IOException {
        
        this.chunkStorage = chunkStorage;
        if(chunkContentFormat != null) {
            this.format = chunkContentFormat.toString();
        } else {
            throw new ASAPException("format expected. like application/x-sn2-makan");
        }
    }

    private void saveStatus() throws IOException {
        if (this.memento != null) {
            this.memento.save(this);
        }
    }

    @Override
    public ASAPChunkStorage getChunkStorage() {
        return this.chunkStorage;
    }
    
    ASAPChunkStorage getStorage() {
        return this.chunkStorage;
    }

    public void attachASAPMessageAddListener(ASAPOnlineMessageSender asapOnlineMessageSender) {
        this.asapOnlineMessageSender = asapOnlineMessageSender;
    }

    public void detachASAPMessageAddListener(ASAPOnlineMessageSender asapOnlineMessageSender) {
        this.asapOnlineMessageSender = null;
    }

    //////////////////////////////////////////////////////////////////////
    //                               Writer                             //
    //////////////////////////////////////////////////////////////////////

    @Override
    public void putExtra(CharSequence uri, String key, String value) throws IOException {
        this.chunkStorage.getChunk(uri, this.era).putExtra(key, value);
    }

    @Override
    public CharSequence removeExtra(CharSequence uri, String key) throws IOException {
        return this.chunkStorage.getChunk(uri, this.era).removeExtra(key);
    }

    @Override
    public CharSequence getExtra(CharSequence uri, String key) throws IOException {
        return this.chunkStorage.getChunk(uri, this.era).getExtra(key);
    }

    @Override
    public void createChannel(CharSequence uri, Set<CharSequence> recipients) throws IOException, ASAPException {
        this.createChannel(this.getOwner(), uri, recipients);
    }

    @Override
    public void createChannel(CharSequence owner, CharSequence uri, Set<CharSequence> recipients)
            throws IOException, ASAPException {

        this.setRecipients(uri, recipients);
        this.getASAPChannelImpl(uri).setOwner(owner);

        // inform recipients about that event
        if(this.isASAPManagementStorageSet()) {
            this.getASAPManagementStorage().notifyChannelCreated(this.format, owner, uri, recipients);
        } else {
            System.out.println(this.getLogStart()
                    + "asap management storage not set - no propagation of channel creation");
        }
    }

    @Override
    public void createChannel(CharSequence urlTarget, CharSequence recipient) throws IOException, ASAPException {
        Set<CharSequence> recipients = new HashSet<>();
        recipients.add(recipient);
        this.createChannel(urlTarget, recipients);
    }

    private ASAPManagementStorage asapManagementStorage = null;
    public void setASAPManagementStorage(ASAPManagementStorage asapManagementStorage) {
        this.asapManagementStorage = asapManagementStorage;
    }

    public ASAPManagementStorage getASAPManagementStorage() throws IOException, ASAPException {
        if(this.asapManagementStorage == null) {
            throw new ASAPException("ASAP Management Storage not set");
        }

        return this.asapManagementStorage;
    }

    public boolean isASAPManagementStorageSet() {
        return this.asapManagementStorage != null;
    }

    public void notifyChannelCreated(CharSequence appName, CharSequence owner,
                                     CharSequence uri, Set<CharSequence> recipients)
            throws ASAPException, IOException {

        new ASAPManagementStorageImpl(this).notifyChannelCreated(appName, owner, uri, recipients);
    }

    public void addRecipient(CharSequence urlTarget, CharSequence recipient) throws IOException {
        this.chunkStorage.getChunk(urlTarget, this.era).addRecipient(recipient);
    }

    public void setRecipients(CharSequence urlTarget, Set<CharSequence> recipients) throws IOException {
        this.chunkStorage.getChunk(urlTarget, this.era).setRecipients(recipients);
    }

    public Set<CharSequence> getRecipients(CharSequence urlTarget) throws IOException {
        return this.chunkStorage.getChunk(urlTarget, this.era).getRecipients();
    }

    public void removeRecipient(CharSequence urlTarget, CharSequence recipients) throws IOException {
        this.chunkStorage.getChunk(urlTarget, this.era).removeRecipient(recipients);
    }

    @Override
    public void add(CharSequence urlTarget, CharSequence message) throws IOException {
        this.add(urlTarget, message.toString().getBytes());
    }

    @Override
    public void add(CharSequence urlTarget, byte[] messageAsBytes) throws IOException {
        ASAPChunk chunk = this.chunkStorage.getChunk(urlTarget, this.era);

        chunk.addMessage(messageAsBytes);

        // remember - something changed in that era
        this.contentChanged();

        if(this.asapOnlineMessageSender != null) {
            try {
                this.asapOnlineMessageSender.sendASAPAssimilate(
                        this.format, urlTarget, chunk.getRecipients(),
                        messageAsBytes, this.era);
            } catch (IOException | ASAPException e) {
                StringBuilder sb = Log.startLog(this);
                sb.append("message written to local storage - but could not write to open asap connection: ");
                sb.append(e.getLocalizedMessage());
                System.err.println(sb.toString());
            }
        }
    }

    private void contentChanged() throws IOException {
        this.contentChanged = true;
        this.saveStatus();
    }

    public List<CharSequence> getChannelURIs() throws IOException {
        List<CharSequence> uriList = new ArrayList<>();

        List<ASAPChunk> chunks = this.chunkStorage.getChunks(this.era);
        for(ASAPChunk chunk : chunks) {
            uriList.add(chunk.getUri());
        }

        return uriList;
    }

    @Override
    public ASAPChannel getChannel(CharSequence uri) throws ASAPException, IOException {
        return this.getASAPChannelImpl(uri);
    }

    private ASAPChannelImpl getASAPChannelImpl(CharSequence uri) throws ASAPException, IOException {
        if(this.channelExists(uri)) {
            return new ASAPChannelImpl(this, uri);
        }

        throw new ASAPException("channel with does not exist: " + uri);
    }

    @Override
    public boolean channelExists(CharSequence uri) throws IOException {
        return this.chunkStorage.existsChunk(uri, this.getEra());
    }

    public void removeChannel(CharSequence uri) throws IOException {
        ASAPChunk chunk = this.chunkStorage.getChunk(uri, this.getEra());
        chunk.drop();
    }

    public ASAPChannelMessages getChunkChain(int position) throws IOException, ASAPException {
        return this.getChunkChain(position, this.era);
    }

    public ASAPChannelMessages getChunkChain(CharSequence uri, int toEra) throws IOException {
        return this.chunkStorage.getASAPChunkCache(uri, toEra);
    }

    public ASAPChannelMessages getChunkChain(CharSequence uri) throws IOException {
        return this.getChunkChain(uri, this.getEra());
    }

    public ASAPChannelMessages getChunkChain(int position, int toEra)
            throws IOException, ASAPException {

        List<CharSequence> channelURIs = this.getChannelURIs();
        if(channelURIs.size() - 1 < position) {
            throw new ASAPException("position greater than number of channels");
        }

        CharSequence uri = channelURIs.get(position);
        if(uri == null) {
            throw new ASAPException("uri at postion is null. Position: " + position);
        }

        return this.chunkStorage.getASAPChunkCache(uri, toEra);
    }

    //////////////////////////////////////////////////////////////////////
    //                       ProtocolEngine                             //
    //////////////////////////////////////////////////////////////////////
    
    List<String> activePeers = new ArrayList<>();
    
    private String getLogStart() {
        StringBuilder b = new StringBuilder();
        b.append("ASAPEngine (");
        b.append(this.owner);
        b.append(", era: ");
        b.append(this.era);
        b.append("): ");

        return b.toString();
    }

    public void handleConnection(InputStream is, OutputStream os,
                                 ASAPChunkReceivedListener listener) {

        try {
            ASAP_1_0 protocol = new ASAP_Modem_Impl();

            // tell who I am - send a interest message
            protocol.interest(this.owner, null, null,
                    null, -1, -1, os, false);

            // read interest from the other side
            ASAP_PDU_1_0 asapPDU = protocol.readPDU(is);

            // must be an interest
            if(asapPDU.getCommand() != ASAP_1_0.INTEREST_CMD) {
                throw new ASAPException("protocol error: expected asap interest - got something else");
            }

            this.handleASAPInterest((ASAP_Interest_PDU_1_0) asapPDU, protocol, os);
        }
        catch(Exception ioe) {
            //<<<<<<<<<<<<<<<<<<debug
            StringBuilder b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("IOEXception: ");
            b.append(ioe.getLocalizedMessage());
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug
        }
    }

    public void handleASAPOffer(ASAP_OfferPDU_1_0 asapOffer, ASAP_1_0 protocol, OutputStream os)
            throws ASAPException, IOException {

        /*
        if(this.isASAPManagementMessage(asapOffer)) {
            //<<<<<<<<<<<<<<<<<<debug
            StringBuilder b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("got asap management assimilate - not handled in this implementation.");
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug
            return;
        } else {
         */
            //<<<<<<<<<<<<<<<<<<debug
            StringBuilder b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("ASAP Offer is not processed in this implementation");
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug
            return;
//        }
    }

    public void handleASAPAssimilate(ASAP_AssimilationPDU_1_0 asapAssimiliationPDU, ASAP_1_0 protocol,
                              InputStream is, OutputStream os, ASAPChunkReceivedListener listener)
            throws ASAPException, IOException {

        String sender = asapAssimiliationPDU.getPeer();
        int eraSender = asapAssimiliationPDU.getEra();

        //<<<<<<<<<<<<<<<<<<debug
        StringBuilder b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("going to assimilate pdu sender: ");
        b.append(sender);
        b.append(" | era: ");
        b.append(eraSender);
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        // get received storage
        ASAPChunkStorage incomingSenderStorage = this.getIncomingChunkStorage(sender);
        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("got incoming chunk storage for sender: ");
        b.append(sender);
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        boolean changed = false;

        try {
            // read URI
            String uri = asapAssimiliationPDU.getChannelUri();

            // get local target for data to come
            ASAPChunk localChunk = null;

            if(!incomingSenderStorage.existsChunk(uri, eraSender)) {
                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(this.getLogStart());
                b.append("no incoming chunk yet | ");
                b.append(asapAssimiliationPDU.toString());
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug

                // is there a local chunk - to clone recipients from?
                if(this.channelExists(uri)) {
                    localChunk = this.getStorage().getChunk(uri, this.getEra());
                }
            }

            ASAPChunk incomingChunk = incomingSenderStorage.getChunk(uri, eraSender);
            if(localChunk != null) {
                System.out.println(this.getLogStart() + "copy local meta data into newly created incoming chunk");
                incomingChunk.copyMetaData(this.getChannel(uri));
            }

            List<Integer> messageOffsets = asapAssimiliationPDU.getMessageOffsets();

            // iterate messages and stream into chunk
            InputStream protocolInputStream = asapAssimiliationPDU.getInputStream();
            long offset = 0;
            for(long nextOffset : messageOffsets) {
                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(this.getLogStart());
                b.append("going to read message: [");
                b.append(offset);
                b.append(", ");
                b.append(nextOffset);
                b.append(")");
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug
                incomingChunk.addMessage(protocolInputStream, nextOffset - offset);
                if(!changed) { changed = true; this.contentChanged();}
                offset = nextOffset;
            }

            // last round
            //<<<<<<<<<<<<<<<<<<debug
            b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("going to read last message: from offset ");
            b.append(offset);
            b.append(" to end of file - total length: ");
            b.append(asapAssimiliationPDU.getLength());
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug

            incomingChunk.addMessage(protocolInputStream, asapAssimiliationPDU.getLength() - offset);
            if(!changed) { changed = true; this.contentChanged();}

            // read all messages
            if(listener != null) {
                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(this.getLogStart());
                b.append("call ");
                b.append(listener.getClass().getSimpleName());
                b.append(".chunkReceived(sender: ");
                b.append(sender);
                b.append(", uri: ");
                b.append(uri);
                b.append(", eraSender: ");
                b.append(eraSender);
                b.append(")");
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug

                listener.chunkReceived(sender, uri, eraSender);
            } else {
                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(this.getLogStart());
                b.append("listener is null - no callback");
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug
            }
        }
        catch (IOException | ASAPException e) {
            b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("Exception (give up, keep streams untouched): ");
            b.append(e.getLocalizedMessage());
            System.out.println(b.toString());
            throw e;
        }
    }

    public void handleASAPInterest(ASAP_Interest_PDU_1_0 asapInterest, ASAP_1_0 protocol, OutputStream os)
            throws ASAPException, IOException {

        // get remote peer
        String peer = asapInterest.getPeer();

        //<<<<<<<<<<<<<<<<<<debug
        StringBuilder b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("handle interest pdu received from ");
        b.append(peer);
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        // check conflict
        if(!this.permission2ProceedConversation(peer)) {
            b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("no permission to communicate with remote peer: ");
            b.append(peer);
            System.err.println(b.toString());
            throw new ASAPException("no permission to communicate with remote peer: " + peer);
        } else {
            System.out.println(this.getLogStart() + "permission ok, process interest");
        }

        // era we are about to transmit
        int workingEra = this.getEraStartSync(peer);
        System.out.println(this.getLogStart() + "last_seen: " + workingEra + " | era: " + this.era);

        if(workingEra == this.era) {
            // nothing todo
            b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("there are no information before that era; ");
            b.append("we only deliver information from previous eras - nothing todo here.");
            System.out.println(b.toString());
            return;
        }

        // we iterate up to era just before current one - current one is active sync.
        int lastEra = this.getPreviousEra(this.era);

        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("workingEra: ");
        b.append(workingEra);
        b.append(" | lastEra: ");
        b.append(lastEra);
        b.append(" | this.era: ");
        b.append(this.era);
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        // make a breakpoint here
        if(this.memento != null) this.memento.save(this);
        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("memento saved");
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        this.sendChunks(this.owner, peer, this.getChunkStorage(), protocol, workingEra, lastEra, os);

        //<<<<<<<<<<<<<<<<<<debug
        b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("ended iterating local chunks");
        System.out.println(b.toString());
        //>>>>>>>>>>>>>>>>>>>debug

        if(this.isSendReceivedChunks()) {
            System.out.println(this.getLogStart() + "send also received chunks - if any");

            for(CharSequence sender : this.getSender()) {
                System.out.println(this.getLogStart() + "send chunks received from: " + sender);
                ASAPChunkStorage incomingChunkStorage = this.getIncomingChunkStorage(sender);

                this.sendChunks(sender, peer, incomingChunkStorage, protocol, workingEra, lastEra, os);
            }
        } else {
            System.out.println(this.getLogStart() + "engine does not send received chunks");
        }
    }

    private boolean isSendReceivedChunks() {
        return this.sendReceivedChunks;
    }

    public void setSendReceivedChunks(boolean on) throws IOException {
        this.sendReceivedChunks = on;
        this.saveStatus();
    }

    private void sendChunks(CharSequence sender, String remotePeer, ASAPChunkStorage chunkStorage,
                            ASAP_1_0 protocol, int workingEra,
                            int lastEra, OutputStream os) throws IOException, ASAPException {
        /*
        There is a little challenge: era uses a circle of numbers
        We cannot say: higher number, later era. That rule does *not*
        apply. We can calculate next era, though.

        That loop has to be re-entered as long as working era has not
        yet reached lastEra. In other words: lastRound is reached whenever
        workingEra == lastEra. Processing lastEra is the last round
        We at at least one round!
        */

        boolean lastRound = false; // assume more than one round
        do {
            boolean goAhead = true; // to avoid deep if-if-if-if structures
            lastRound = workingEra == lastEra;

            List<ASAPChunk> chunks = chunkStorage.getChunks(workingEra);
            //<<<<<<<<<<<<<<<<<<debug
            StringBuilder b = new StringBuilder();
            b.append(this.getLogStart());
            b.append("start iterating chunks with working Era: ");
            b.append(workingEra);
            System.out.println(b.toString());
            //>>>>>>>>>>>>>>>>>>>debug

            for(ASAPChunk chunk : chunks) {
                //<<<<<<<<<<<<<<<<<<debug
                b = new StringBuilder();
                b.append(this.getLogStart());
                b.append("chunkUrl: ");
                b.append(chunk.getUri());
                b.append(" | isPublic: ");
                b.append(this.isPublic(chunk));
                b.append(" | len: ");
                b.append(chunk.getLength());
                System.out.println(b.toString());
                //>>>>>>>>>>>>>>>>>>>debug

                if(chunk.getLength() < 1) {
                    goAhead = false;
                }

                // is not a public chunk
                if (goAhead && !this.isPublic(chunk)) {
                    Set<CharSequence> recipients = chunk.getRecipients();
                    if (recipients == null || !recipients.contains(remotePeer)) {
                        goAhead = false;
                    }
                }

                if (goAhead) {
                    //<<<<<<<<<<<<<<<<<<debug
                    b = new StringBuilder();
                    b.append(this.getLogStart());
                    b.append("send chunk");
                    System.out.println(b.toString());
                    //>>>>>>>>>>>>>>>>>>>debug

                    protocol.assimilate(sender, // remotePeer
                            remotePeer, // remotePeer
                            this.format,
                            chunk.getUri(), // channel ok
                            workingEra, // era ok
                            chunk.getLength(), // data length
                            chunk.getOffsetList(),
                            chunk.getMessageInputStream(),
                            os,
                            false);

                    // remember sent
                    chunk.deliveredTo(remotePeer);
                    //<<<<<<<<<<<<<<<<<<debug
                    b = new StringBuilder();
                    b.append(this.getLogStart());
                    b.append("remembered delivered to ");
                    b.append(remotePeer);
                    System.out.println(b.toString());
                    //>>>>>>>>>>>>>>>>>>>debug
                    // sent to all recipients
                    if (chunk.getRecipients().size() == chunk.getDeliveredTo().size()) {
                        b = Log.startLog(this);
                        b.append("#recipients == #deliveredTo chunk delivered to any potential remotePeer - could drop it");
                        System.out.println(b.toString());
                        if (this.isDropDeliveredChunks()) {
                            chunk.drop();
                            //<<<<<<<<<<<<<<<<<<debug
                            b = Log.startLog(this);
                            b.append("chunk dropped");
                            System.out.println(b.toString());
                        } else {
                            b = Log.startLog(this);
                            b.append("drop flag set false - engine does not remove delivered chunks");
                            System.out.println(b.toString());
                        }
                    }
                } else {
                    System.out.println(this.getLogStart() + "nothing sent: empty or not on recipient list");
                }
            }

            // remember that we are in sync until that era
            this.setLastSeen(remotePeer, workingEra);

            // make a breakpoint here
            if(this.memento != null) this.memento.save(this);

            // next era which isn't necessarilly workingEra++
            workingEra = this.getNextEra(workingEra);

            // as long as not already performed last round
        } while(!lastRound);
    }

    private boolean isDropDeliveredChunks() {
        return this.dropDeliveredChunks;
    }

    public void setDropDeliveredChunks(boolean drop) throws IOException {
        this.dropDeliveredChunks = drop;
        this.saveStatus();
    }

    static int nextEra(int workingEra) {
        if(workingEra == Integer.MAX_VALUE) {
            return 0;
        }
        
        return workingEra+1;
    }
    
    @Override
    public int getNextEra(int workingEra) {
        return ASAPEngine.nextEra(workingEra);
    }

    static int previousEra(int workingEra) {
        if(workingEra == 0) {
            return Integer.MAX_VALUE;
        }
        
        return workingEra-1;
    }
    
    @Override
    public int getPreviousEra(int workingEra) {
        return ASAPEngine.previousEra(workingEra);
    }

    private int getEraStartSync(String peer) {
        Integer lastEra = this.lastSeen.get(peer);
        if(lastEra == null) {
            return this.getOldestEra();
        }
        
        return lastEra;
    }

    private void setLastSeen(String peer, int workingEra) {
        this.lastSeen.put(peer, era);
    }

    @Override
    public int getOldestEra() {
        return this.oldestEra;
    }
    
    @Override
    public int getEra() {
        return this.era;
    }
    
//    public int getNextEra(int era) {
//        return this.
//    }
    
    /**
     * Peers in ad-hoc networks have the tendency to establish two channels
     * simultaneously. It's due to the lack of a controlling server instance
     * and the - wanted - equality of each peers. We should suppress those
     * useless double conversations.
     * 
     * @param peer
     * @return 
     */
    private synchronized boolean permission2ProceedConversation(String peer) {
        // if that peer is not in the list - go ahead
        boolean goAhead = !this.activePeers.contains(peer);
        if(goAhead) {
            // add that peer - an other call will fail.
            this.activePeers.add(peer);
        }
        
        return goAhead;
    }

    /**
     * We interpret an existing chunk with *no* recipients as
     * public chunk
     * @param chunk
     * @return 
     */
    private boolean isPublic(ASAPChunk chunk) {
        return chunk.getRecipients().isEmpty();
    }

    @Override
    public void newEra() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getLogStart());
        sb.append("newEra() | owner: ");
        sb.append(this.owner);
        sb.append(" | format: ");
        sb.append(this.format);
        sb.append(" | ");

        if(this.contentChanged) {
            sb.append("content changed - increment era...");
            System.out.println(sb.toString());
            try {
                int oldEra = this.era;
                int nextEra = this.getNextEra(this.era);

                // set as fast as possible to make race conditions less likely
                this.contentChanged = false;

                // we are done here - we are in a new era.
                this.era = nextEra;

                // persistent values
                if(this.memento != null) this.memento.save(this);

                // drop very very old chunks - if available
                this.chunkStorage.dropChunks(nextEra);

                // setup new era - copy all chunks
                for(ASAPChunk chunk : this.chunkStorage.getChunks(oldEra)) {
                    ASAPChunk copyChunk = this.chunkStorage.getChunk(chunk.getUri(), nextEra);
                    copyChunk.clone(chunk);
                }

                System.out.println(this.getLogStart() + "era incremented");
            } catch (IOException ex) {
                sb.append("IOException while incrementing era: ");
                sb.append(ex.getLocalizedMessage());
                System.err.println(sb.toString());
            }
        } else {
            sb.append("content not changed - era not changed");
            System.out.println(sb.toString());
        }
    }
}