package;

#if android
import openfl.display.Sprite;
import openfl.events.Event;
import openfl.events.KeyboardEvent;
import openfl.events.TouchEvent;
import openfl.ui.Multitouch;
import openfl.ui.MultitouchInputMode;
import openfl.text.TextField;
import openfl.text.TextFormat;

/** Stage-space controls keep their size and position across gameplay cameras. */
class MobileControls extends Sprite {
	var fingers:Map<Int, Int> = [];
	var held:Map<Int, Int> = [];
	var buttons:Array<Sprite> = [];
	var keys:Array<Int> = [37, 40, 38, 39, 32, 13, 27];
	var labels:Array<String> = ["<", "v", "^", ">", "DODGE", "OK / PAUSE", "BACK"];

	public function new() {
		super();
		Multitouch.inputMode = MultitouchInputMode.TOUCH_POINT;
		addEventListener(Event.ADDED_TO_STAGE, setup);
	}

	function setup(event:Event):Void {
		removeEventListener(Event.ADDED_TO_STAGE, setup);
		for (i in 0...keys.length) {
			var button = new Sprite();
			button.mouseChildren = false;
			var text = new TextField();
			text.defaultTextFormat = new TextFormat("_sans", 18, 0xFFFFFF, true);
			text.text = labels[i];
			text.selectable = false;
			text.mouseEnabled = false;
			button.addChild(text);
			buttons.push(button);
			addChild(button);
			button.addEventListener(TouchEvent.TOUCH_BEGIN, function(e:TouchEvent) {
				if (fingers.exists(e.touchPointID))
					return;
				fingers.set(e.touchPointID, i);
				var count = held.exists(i) ? held.get(i) : 0;
				held.set(i, count + 1);
				button.alpha = 1;
				if (count == 0)
					send(i, true);
			});
		}
		stage.addEventListener(TouchEvent.TOUCH_END, release);
		stage.addEventListener(Event.DEACTIVATE, reset);
		stage.addEventListener(Event.RESIZE, layout);
		flixel.FlxG.signals.preStateSwitch.add(function() reset(null));
		layout(null);
	}

	function send(index:Int, down:Bool):Void {
		stage.dispatchEvent(new KeyboardEvent(down ? KeyboardEvent.KEY_DOWN : KeyboardEvent.KEY_UP, true, false, 0, keys[index]));
	}

	function release(event:TouchEvent):Void {
		if (!fingers.exists(event.touchPointID))
			return;
		var index = fingers.get(event.touchPointID);
		fingers.remove(event.touchPointID);
		var count = held.get(index) - 1;
		held.set(index, count);
		if (count == 0) {
			buttons[index].alpha = 0.55;
			send(index, false);
		}
	}

	function reset(event:Event):Void {
		for (index in held.keys()) {
			if (held.get(index) > 0)
				send(index, false);
			buttons[index].alpha = 0.55;
		}
		fingers.clear();
		held.clear();
	}

	function layout(event:Event):Void {
		var size = Math.min(stage.stageHeight * 0.19, stage.stageWidth / 8);
		for (i in 0...buttons.length) {
			var button = buttons[i];
			button.graphics.clear();
			button.graphics.beginFill(0x202020, 0.85);
			button.graphics.lineStyle(2, 0xFFFFFF);
			button.graphics.drawRoundRect(0, 0, size, size, 12);
			button.graphics.endFill();
			button.alpha = 0.55;
			button.x = i < 4 ? 12 + i * (size + 8) : stage.stageWidth - (7 - i) * (size + 8);
			button.y = i < 4 ? stage.stageHeight - size - 12 : 12;
			var text:TextField = cast button.getChildAt(0);
			text.width = size;
			text.height = size;
			text.y = size / 3;
		}
	}
}
#end
