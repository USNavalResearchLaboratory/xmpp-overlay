import json
import socket
import struct
import threading

class TransportEngineAPI(threading.Thread) :
    def __init__(self, callback = None, ip='127.0.0.1', port=1998) :
        self.callback = callback
        self.ip = ip
        self.port = port

        self.socket = None
        self.running = False

    def start(self) :
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((self.ip, self.port))
        self.running = True
        threading.Thread(target=self._recv).start()

    def send(self, id, dest, payload) :
        self._send(self._wrap('send', { 'id' : id, 'destination' : dest, 'payload' : payload }))

    def change_properties(self, reliable, persistent, ordered) :
        self._send(self._wrap('set-properties', { 'reliable' : self._bool_str(reliable), 'persistent' : persistent, 'ordered' : self._bool_str(ordered) }))

    def set_subscription(self, dest, subscribe) :
        self._send(self._wrap('subscription', { 'destination' : dest, 'subscribe' : self._bool_str(subscribe) }))

    def end_session(self) :
        self._send(self._wrap('end-session', {}))

    def _send(self, data) :
        s = json.dumps(data).encode('utf-8')
        self.sock.send(struct.pack('!i', len(s)))
        self.sock.send(s)

    def _recv(self) :
        while self.running :
            try :
                b = self._recv_all(4)
                length = struct.unpack('!i', b)[0]
                data = self._recv_all(length)
                if self.callback != None :
                    self.callback(data)
            except :
                print('Error receiving message.')

    def _recv_all(self, size) :
        msg = b''
        while len(msg) < size :
            segment = self.sock.recv(size - len(msg))
            if len(segment) == 0 :
                break
            msg += segment

        return msg

    @staticmethod
    def _wrap(cmd, args) :
        return { 'command' : cmd, 'args' : args }

    @staticmethod
    def _bool_str(boolean) :
        if boolean :
            return 'true'
        return 'false'

if __name__ == '__main__' :
    te = TransportEngineAPI()
    te.start()
    te.set_subscription('testchannel', True)
    te.change_properties(False, 0, False)
    te.send('1', 'testchannel', 'this is the payload')
    te.end_session()
