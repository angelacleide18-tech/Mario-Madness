package;

class CppAPI {
	#if cpp
	public static function obtainRAM():Int {
		return WindowsData.obtainRAM();
	}

	public static function darkMode() {
		#if windows
		WindowsData.setWindowColorMode(DARK);
		#end
	}

	public static function lightMode() {
		#if windows
		WindowsData.setWindowColorMode(LIGHT);
		#end
	}

	public static function setWindowOppacity(a:Float) {
		#if windows
		WindowsData.setWindowAlpha(a);
		#end
	}

	public static function _setWindowLayered() {
		#if windows
		WindowsData._setWindowLayered();
		#end
	}

	public static function setWallpaper(path:String) {
		if (path == 'old') {
			if (Wallpaper.oldWallpaper != null) {
				path = Wallpaper.oldWallpaper;
			}
			else {
				return;
			}
		}
		Wallpaper.setWallpaper(path);
	}

	public static function setOld() {
		Wallpaper.setOld();
	}

	public static function hideTaskbar() {
		#if windows
		WindowsData.hideTaskbar();
		#end
	}

	public static function restoreTaskbar() {
		#if windows
		WindowsData.restoreTaskbar();
		#end
	}

	public static function hideWindows() {
		#if windows
		WindowsData.hideWindows();
		#end
	}

	public static function restoreWindows() {
		#if windows
		WindowsData.restoreWindows();
		#end
	}

	public static function setTransparency(winName:String, color:Int) {
		Transparency.setTransparency(winName, color);
	}

	public static function removeWindowIcon() {
		#if windows
		WindowsData.removeWindowIcon();
		#end
	}

	public static function reset() {
		Transparency.reset();
	}

	public static function allowHighDPI() {
		#if windows
		WindowsData.registerHighDpi();
		#end
	}
	#end
}
