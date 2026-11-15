package app.nook.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.HorizontalScrollView;

/** Restore standard focus audio when HorizontalScrollView consumes a DPAD move. */
class ShelfScrollView extends HorizontalScrollView {
    ShelfScrollView(Context context){super(context);}
    @Override public boolean executeKeyEvent(KeyEvent event){
        View before=findFocus();
        boolean handled=super.executeKeyEvent(event);
        int key=event.getKeyCode();
        if(handled&&event.getAction()==KeyEvent.ACTION_DOWN&&before!=findFocus()
                &&isSoundEffectsEnabled()&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(key==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT));
        }
        return handled;
    }
}
