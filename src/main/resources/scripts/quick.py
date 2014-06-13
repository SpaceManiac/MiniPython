# quick.py
# decorator-based quick plugin setup system

from org.bukkit.command import CommandExecutor
from org.bukkit.event import EventPriority

# access 'quick.plugin' from methods to get plugin object
plugin = None

# enable, disable, command, and event hooks
_enable = []
def enable(func):
    _enable.append(func)
    return func

_disable = []
def disable(func):
    _disable.append(func)
    return func

_commands = []
def command(name):
    def wrap(func):
        _commands.append((name, func))
        return func
    return wrap

_events = []
def event(event, priority=EventPriority.NORMAL, ignore_cancelled=False):
    def wrap(func):
        _events.append(EventHandler(event, priority, ignore_cancelled)(func))
        return func
    return wrap

# wrappers for commands and events
class _Executor(CommandExecutor):
    def __init__(self, func):
        self.func = func

    def onCommand(self, sender, command_obj, label, args):
        # if the function returns None, assume True result
        result = self.func(sender, command_obj, label, args)
        return True if result is None else result

class _Listener(Listener):
    def __init__(self, functions):
        for i, func in enumerate(functions):
            setattr(self, 'handler_%d' % i, func)

# function to generate plugin class
def _class():
    class QuickPlugin(PythonPlugin):
        def onEnable(self):
            for name, func in _commands:
                cmd = self.getCommand(name)
                if cmd is None:
                    self.logger.severe("Failed to set quick command: unknown command \"" + name + "\"")
                else:
                    cmd.setExecutor(_Executor(func))

            if _events:
                self.server.pluginManager.registerEvents(_Listener(_events), self)

            for func in _enable:
                func()

        def onDisable(self):
            for func in _disable:
                func()

    return QuickPlugin
