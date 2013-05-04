# decorators.py
# decorators emulating a few Bukkit annotations

# only useful to put on classes deriving Listener
def EventHandler(type, priority=EventPriority.NORMAL, ignoreCancelled=False):
	# resolve the type to Bukkit form
	try:
		type = __import__('org.bukkit.event.' + type)
	
	# resolve the priority to Bukkit form
	if not isinstance(priority, EventPriority):		
		priority = str(priority).upper()
		if not hasattr(EventPriority, priority):
			raise Exception("No such priority " + priority)
		priority = getattr(EventPriority, priority)
	
	# wrap the function
	def wrap(func):
		if hasattr(func, 'bukkit_eventhandler'):
			handlers = func.bukkit_eventhandler
		else:
			handlers = []
		handlers.append((type, priority, ignoreCancelled))
		func.bukkit_eventhandler = handlers
	return wrap
