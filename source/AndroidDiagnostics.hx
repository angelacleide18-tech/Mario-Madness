package;

#if (android && android_diagnostics)
import lime.graphics.opengl.GL;
#end

/** Optional startup evidence; normal builds compile these calls away. */
class AndroidDiagnostics {
	public static inline function mark(message:String):Void {
		#if (android && android_diagnostics)
		try {
			var write = lime.system.JNI.createStaticMethod("com/mariomadness/mobile/MainActivity", "mark", "(Ljava/lang/String;)V");
			write(message);
		}
		catch (error:Dynamic) {
			trace('Could not write startup diagnostic: $error');
		}
		#end
	}

	public static function install():Void {
		#if (android && android_diagnostics)
		mark("Haxe Main.main");
		openfl.Lib.current.loaderInfo.uncaughtErrorEvents.addEventListener(openfl.events.UncaughtErrorEvent.UNCAUGHT_ERROR, function(event) {
			mark("Haxe uncaught error: " + Std.string(event.error) + "\n" + haxe.CallStack.toString(haxe.CallStack.exceptionStack()));
		});
		mark("GL renderer: " + GL.getParameter(GL.RENDERER) + "; version: " + GL.getParameter(GL.VERSION) + "; max texture: "
			+ GL.getParameter(GL.MAX_TEXTURE_SIZE));
		#end
	}
}
