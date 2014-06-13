# _setup.py
# setup code for new interpreters during Python loading

# loader sets _plugin_name before running this script raw
# this check prevents this from being imported as a module
if not '_plugin_name' in locals():
    raise ImportError("cannot import _setup as module")

# keep everything scoped nicely
def _setup():
    from com.platymuus.bukkit.minipython.loader import PythonPlugin
    from org.bukkit.event import EventPriority, Listener
    from org.bukkit import Bukkit

    # redirect Python stdout through Bukkit logger
    import sys
    class StdoutRedirect(object):
        def __init__(self, func, name):
            self.buffer = ''
            self.func = func
            self.name = name

        def write(self, text):
            self.buffer += text.replace('\r', '')
            index = self.buffer.find('\n')
            while index >= 0:
                self.func('[%s] %s' % (self.name, self.buffer[:index]))
                self.buffer = self.buffer[index+1:]
                index = self.buffer.find('\n')

    sys.stdout = StdoutRedirect(Bukkit.getLogger().info, _plugin_name)
    sys.stderr = StdoutRedirect(Bukkit.getLogger().severe, _plugin_name)

    # decorator that marks functions as event handlers
    # apply to functions on classes extending Listener and register normally
    def EventHandler(event, priority=EventPriority.NORMAL, ignore_cancelled=False):
        # we let the Java side handle resolving type to a class

        # resolve the priority to Bukkit form
        if not isinstance(priority, EventPriority):
            priority = str(priority).upper()
            if not priority in ('LOWEST', 'LOW', 'NORMAL', 'HIGH', 'HIGHEST', 'MONITOR'):
                raise KeyError("No such priority " + priority)
            priority = getattr(EventPriority, priority)

        # wrap the function
        def wrap(func):
            handlers = getattr(func, 'bukkit_eventhandler', [])
            handlers.append((event, priority, ignore_cancelled))
            func.bukkit_eventhandler = handlers
            return func

        return wrap

    # place a few essentials into builtins
    import __builtin__
    __builtin__.PythonPlugin = PythonPlugin
    __builtin__.Listener = Listener
    __builtin__.EventHandler = EventHandler
    __builtin__.Bukkit = Bukkit

# do the things
_setup()
del _setup
del _plugin_name
