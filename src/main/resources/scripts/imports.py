# imports.py
# built-in imports and overrides for Python plugins

from com.platymuus.bukkit.minipython.loader import PythonPlugin
from org.bukkit.event import EventPriority, Listener
import org.bukkit as bukkit

import sys

# without this, Bukkit's existing output redirect will cause 'print' to generate extra newlines
# this is really a Bukkit bug and not a Python-specific issue
class _StdoutRedirect(object):
    def write(self, text):
        if text.endswith("\n"):
            sys.__stdout__.write(text[:-1])
            sys.__stdout__.flush()
        else:
            sys.__stdout__.write(text)
sys.stdout = _StdoutRedirect()

server = bukkit.Bukkit.getServer()

# bake some values into __builtin__ so they're referencable anywhere
def buildin(name, value):
	import __builtin__
	setattr(__builtin__, name, value)
	
buildin('PythonPlugin', PythonPlugin)
buildin('PythonListener', Listener)
buildin('Listener', Listener)
