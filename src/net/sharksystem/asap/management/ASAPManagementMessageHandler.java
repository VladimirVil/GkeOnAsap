package net.sharksystem.asap.management;

import net.sharksystem.asap.*;
import net.sharksystem.asap.protocol.ASAP_1_0;

import java.io.IOException;
import java.util.*;

public class ASAPManagementMessageHandler implements ASAPChunkReceivedListener {
    private final MultiASAPEngineFS multiASAPEngine;

    public ASAPManagementMessageHandler(MultiASAPEngineFS multiASAPEngine) throws IOException, ASAPException {
        this.multiASAPEngine = multiASAPEngine;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //                            chunk received listener for asap management engine                     //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    private HashMap<Set<CharSequence>, CharSequence> recipientUris = new HashMap<>();

    public static CharSequence createUniqueUri() {
        return "sn2://asapManagement://" + Long.toString(System.currentTimeMillis());
    }

    CharSequence getURI(Set<CharSequence> recipients) throws IOException, ASAPException {
        // find entry with all recipients - and only those recipients

        for(Set<CharSequence> rSet : recipientUris.keySet()) {
            // are recipients fully inside rSet?
            if(rSet.containsAll(recipients) && recipients.containsAll(rSet)) {
                return recipientUris.get(rSet);
            }
        }

        return null;
    }

    @Override
    public void chunkReceived(String sender, String uri, int era) {
        System.out.println(this.getLogStart()
                + "handle received chunk (sender|uri|era) " + sender + "|" + uri + "|" + era);
        try {
            ASAPEngine asapManagementEngine = multiASAPEngine.getEngineByFormat(ASAP_1_0.ASAP_MANAGEMENT_FORMAT);

            ASAPChunkStorage incomingChunkStorage = asapManagementEngine.getIncomingChunkStorage(sender);
            ASAPChunk chunk = incomingChunkStorage.getChunk(uri, era);
            Iterator<byte[]> messageIter = chunk.getMessagesAsBytes();
            System.out.println(this.getLogStart() + "iterate management messages");
            while(messageIter.hasNext()) {
                byte[] message = messageIter.next();
                Set<CharSequence> recipients = this.handleASAPManagementMessage(message);

                // add message without changes - could be signed
                CharSequence sendUri = this.getURI(recipients);
                boolean setUpRecipients = (sendUri == null);
                if(setUpRecipients) {
                    sendUri = createUniqueUri();
                }
                // write message
                System.out.println(this.getLogStart() + "add received message locally: ");
                asapManagementEngine.add(sendUri, message);

                if(setUpRecipients) {
                    asapManagementEngine.setRecipients(sendUri, recipients);
                    this.recipientUris.put(recipients, sendUri);
                }
            }
            System.out.println(this.getLogStart() + "done iterating management messages");
            // remove incoming messages - handled
            asapManagementEngine.getIncomingChunkStorage(sender).dropChunks(era);
            System.out.println(this.getLogStart() + "incoming asap management messages dropped");
        } catch (ASAPException | IOException e) {
            System.out.println("could get asap management engine but received chunk - looks like a bug");
        }
    }

    private Set<CharSequence> handleASAPManagementMessage(byte[] message) throws ASAPException, IOException {
        StringBuilder b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("start processing asap management pdu");
        System.out.println(b.toString());

        ASAPManagementCreateASAPStorageMessage asapManagementCreateASAPStorageMessage =
                ASAPManagementMessage.parseASAPManagementMessage(message);

        CharSequence owner = asapManagementCreateASAPStorageMessage.getOwner();
        CharSequence channelUri = asapManagementCreateASAPStorageMessage.getChannelUri();
        CharSequence format = asapManagementCreateASAPStorageMessage.getAppName();
        Set<CharSequence> receivedRecipients = asapManagementCreateASAPStorageMessage.getRecipients();

        // add owner to this list
        Set<CharSequence> recipients = new HashSet<>();

        // add owner
        recipients.add(owner);

        // add rest
        for(CharSequence r : receivedRecipients) {
            recipients.add(r);
        }

        b = new StringBuilder();
        b.append(this.getLogStart());
        b.append("owner: ");
        b.append(owner);
        b.append(" | format: ");
        b.append(format);
        b.append(" | channelUri: ");
        b.append(channelUri);
        b.append(" | #recipients: ");
        b.append(recipients.size());

        // find storage / app - can throw an exception - that's ok
        ASAPStorage asapStorage = this.multiASAPEngine.getEngineByFormat(format);

        if(asapStorage.channelExists(channelUri)) {
            Set<CharSequence> existingChannelRecipientsList = asapStorage.getRecipients(channelUri);
            if(existingChannelRecipientsList.size() == recipients.size()) {
                // could be the same - same recipients?

                // iterate all recipient and check whether they are also in local recipient list
                for(CharSequence recipient : recipients) {
                    boolean found = false;
                    for(CharSequence existingRecipient : existingChannelRecipientsList) {
                        if(existingRecipient.toString().equalsIgnoreCase(recipient.toString())) {
                            // got it
                            found = true;
                            break; // leave loop and test next
                        }
                    }
                    if(!found) {
                        throw new ASAPException("channel already exists but with different recipients: " + b.toString());
                    }
                }
                // ok it the same
                return receivedRecipients;
            } else {
                throw new ASAPException("channel already exists but with different settings: " + b.toString());
            }
        }

        // else - channel does not exist - create by setting recipients
        System.out.println(this.getLogStart() + "create channel: " + b.toString());
        asapStorage.createChannel(owner, channelUri, recipients);

        return receivedRecipients;
    }

    private String getLogStart() {
        return this.getClass().getSimpleName() + "(" + this.multiASAPEngine.getOwner() + "): ";
    }
}
