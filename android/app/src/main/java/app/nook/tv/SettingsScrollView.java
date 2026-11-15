package app.nook.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.ScrollView;

/** Restore system navigation audio for vertical moves consumed by Settings scrolling. */
class SettingsScrollView extends ScrollView {
    SettingsScrollView(Context context){super(context);setVerticalScrollBarEnabled(false);}
    @Override public boolean executeKeyEvent(KeyEvent event){
        boolean handled=super.executeKeyEvent(event);
        int key=event.getKeyCode();
        // ScrollView reports handled only when its arrow/full-scroll operation moves focus or scrolls.
        if(handled&&event.getAction()==KeyEvent.ACTION_DOWN&&isSoundEffectsEnabled()
                &&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN));
        }
        return handled;
    }
}
