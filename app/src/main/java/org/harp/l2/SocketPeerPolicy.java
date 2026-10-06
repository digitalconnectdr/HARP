package org.harp.l2;

import java.io.IOException;
import java.net.Socket;

/** Admission policy for sockets accepted by the HARP relay. */
interface SocketPeerPolicy {
    void validate(Socket socket) throws IOException;
}
