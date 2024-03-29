package net.sharksystem.asap;

import net.sharksystem.asap.protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * There is an ASAPEngine that stores its data with a filesystem.
 * One significant parameter is a root directory.
 *
 * It is good practice to use a different root for each application.
 *
 * It is also common that more than one ASAP based app is running
 * on one machine. Thus, different ASAP filesystem based engine are
 * to deal with the data depending on the ASAP format.
 *
 * That interface hides those different engines.
 */
public interface MultiASAPEngineFS {
    public static final long DEFAULT_MAX_PROCESSING_TIME = Long.MAX_VALUE;

    public ASAPEngine getEngineByFormat(CharSequence format) throws ASAPException, IOException;

    ASAPChunkReceivedListener getListenerByFormat(CharSequence format) throws ASAPException;

    /**
     * get or create engine for a given application - mainly means: setup folder
     * @param appName
     * @return
     */
    ASAPEngine getASAPEngine(CharSequence appName, CharSequence format) throws IOException, ASAPException;

    /**
     * handle that newly established connection to another ASAP peer
     * @param is
     * @param os
     * @throws IOException
     * @throws ASAPException
     */
    public ASAPConnection handleConnection(InputStream is, OutputStream os) throws IOException, ASAPException;

    public void pushInterests(OutputStream os) throws IOException, ASAPException;

    Set<CharSequence> getOnlinePeers();

    boolean existASAPConnection(CharSequence recipient);

    ASAPConnection getASAPConnection(CharSequence recipient);

    CharSequence getOwner();

    void newEra() throws IOException, ASAPException;

    void setASAPChunkReceivedListener(CharSequence appName, ASAPChunkReceivedListener listener) throws ASAPException;

    void addOnlinePeersChangedListener(ASAPOnlinePeersChangedListener listener);

    void removeOnlinePeersChangedListener(ASAPOnlinePeersChangedListener listener);

    /**
     * @return true if the asap management engine is up and running
     */
    boolean isASAPManagementEngineRunning();

    EngineSetting getEngineSettings(CharSequence format) throws ASAPException;

    /**
     * @return all formats currently supported by this mulit engine
     */
    Set<CharSequence> getFormats();
}
