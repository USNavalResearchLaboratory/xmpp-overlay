#!/usr/bin/env python3

import argparse
import json
import sys
import time
from screen import Screen
from transport_engine import *

class ChatClient :
    def __init__(self, name, room) :
        self.message_id = 0
        self.name = name
        self.te_api = TransportEngineAPI(self.on_api_message)
        self.screen = Screen(self.on_client_message)
        self.room = room

    def on_api_message(self, message) :
        jmsg = json.loads(message.decode('utf-8'))
        if jmsg['type'] == 'message' :
            send_time, src, message = jmsg['info']['payload'].split(' ', 2)
            if src == self.name :
                src = '* {}'.format(src)
            delta = round(time.time() - float(send_time), 3)
            if delta < 1000 :
                delta = '{}s'.format(delta)
            else :
                delta = '{}m'.format(delta / 60.0)
            self.screen.add_line('{} (after {}): {}'.format(src, delta, message))
        elif jmsg['type'] == 'error' :
            self.screen.add_line('ERROR: {}'.format(message))

    def on_client_message(self, message) :
        if message.startswith('/') :
            if message.startswith('/join') :
                self._join_room(message.split()[1])
            elif message.startswith('/persist') :
                self.te_api.change_properties(False, 1, False)
            elif message.startswith('/nopersist') :
                self.te_api.change_properties(False, 0, False)
            elif message.startswith('/exit') :
                self._exit()
            else :
                self.screen.add_line('commands:', 'blue')
                self.screen.add_line('  /join [room] - Leaves the current room and joins [room].', 'blue')
                self.screen.add_line('  /persist - Makes further messages persistent.', 'blue')
                self.screen.add_line('  /nopersist - Makes further messages non-persistent.', 'blue')
                self.screen.add_line('  /exit - Exits the client.', 'blue')
                self.screen.add_line('  /help - Displays this help message.', 'blue')
        else :
            self._send_message(message)

    def _join_room(self, room) :
        if self.room != None :
            self.te_api.set_subscription(self.room, False)
        self.room = room
        self.te_api.set_subscription(self.room, True)
        self.screen.add_line('--- Now in room "{}" ---'.format(self.room))

    def _exit(self) :
        self.te_api.end_session()
        sys.exit()

    def _send_message(self, message) :
        self.te_api.send(self.message_id, self.room, '{} {} {}'.format(time.time(), self.name, message))
        self.message_id += 1

    def start(self) :
        self.te_api.start()
        self.te_api.change_properties(False, 0, False)
        t = self.screen.start()
        self._join_room(self.room)
        t.join()

if __name__ == '__main__' :
    parser = argparse.ArgumentParser(description='Starts the chat client.')
    parser.add_argument('username', help='Username for chat client.')
    parser.add_argument('room', help='Initial chat room to join.')
    args = vars(parser.parse_args())

    c = ChatClient(args['username'], args['room'])
    c.start()
