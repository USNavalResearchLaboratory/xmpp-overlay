import time
import signal
import urwid, urwid.curses_display
from threading import Thread

class Screen :
    def __init__(self, message_cb=None):
        self._tui = urwid.curses_display.Screen()
        self._tui.register_palette_entry('red', 'dark red', 'default')
        self._tui.register_palette_entry('green', 'dark green', 'default')
        self._tui.register_palette_entry('white', 'white', 'default')
        self._tui.register_palette_entry('aqua', 'light cyan', 'default')
        self._tui.register_palette_entry('gray', 'dark gray', 'default')
        self._tui.register_palette_entry('rose', 'light red', 'default')
        self._tui.register_palette_entry('purple', 'dark magenta', 'default')
        self._tui.register_palette_entry('lime', 'light green', 'default')
        self._tui.register_palette_entry('sky', 'light blue', 'default')
        self._tui.register_palette_entry('blue', 'dark blue', 'default')
        self._tui.register_palette_entry('teal', 'dark cyan', 'default')
        self._tui.register_palette_entry('black', 'black', 'default')
        #
        # If you register more palette entries, above,
        # please add the color names _palette, below.
        # _palette is used by had_color() to determine
        # if a proposed color is one that this Screen can render.
        #
        self._palette = set(['red', 'green', 'white', 
                             'aqua', 'gray', 'rose',
                             'purple', 'lime', 'sky',
                             'blue', 'teal', 'black',
                             'title'])
        self._message_cb = message_cb

    def start(self) :
        signal.signal(signal.SIGINT, signal.SIG_DFL)
        t = Thread(target=self._tui.run_wrapper, args=(self.run,))
        t.start()
        return t

    def add_line(self, line, palette='default') :
        self._lines.append(urwid.Text((palette, line)))
        self._listbox.set_focus(len(self._lines) - 1)
        self.redisplay()

    def add_message(self, msg, palette):
        self.add_line('%s' % msg, palette)

    def has_color(self, color):
        return color in self._palette

    def redisplay(self):
        canvas = self._frame.render(self._size)
        self._tui.draw_screen(self._size, canvas)

    def run(self):
        self._size = self._tui.get_cols_rows()
        self._lines = []
        self._listbox = urwid.ListBox(self._lines)
        self._eb = urwid.Edit(('red', '> '))
        self._frame = urwid.Frame(self._listbox, footer=self._eb)
        self.redisplay()
        
        while True :
            keys = self._tui.get_input()
            if len(keys) > 0 :
                for k in keys :
                    if k == 'enter' :
                        line = self._eb.get_edit_text()
                        self._eb.set_edit_text('')
                        if self._message_cb != None :
                            self._message_cb(line)
                    elif k == 'backspace' :
                        self._eb.set_edit_text(self._eb.get_edit_text()[:-1])
                    elif len(k) == 1 :
                        self._eb.set_edit_text(self._eb.get_edit_text() + k)
                    self.redisplay()

if __name__ == '__main__':
    screen = Screen()
    screen.start()
    screen.add_message('sender', 'message', 'red')
    screen.add_message('sender', 'message', 'green')
