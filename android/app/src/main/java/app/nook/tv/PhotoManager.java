package app.nook.tv;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PhotoManager {
    static final class ImportResult {
        final List<NookStore.Photo> added=new ArrayList<>();
        int skipped;
    }
    private final Context context;
    private final NookStore store;
    PhotoManager(Context context,NookStore store) {this.context=context;this.store=store;}
    ImportResult importFiles(List<Uri> uris,int existingCount) {
        ImportResult result=new ImportResult();long total=0;
        File[] existing=store.photoDirectory.listFiles();if(existing!=null)for(File file:existing)total+=file.length();
        for(Uri uri:uris) {
            if(existingCount+result.added.size()>=50){result.skipped++;continue;}
            File file=new File(store.photoDirectory,UUID.randomUUID()+".img");boolean accepted=false;
            try {
                long bytes=0;
                try(InputStream input=context.getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(file)) {
                    if(input==null)throw new IOException("Photo unavailable");
                    byte[] buffer=new byte[8192];int count;
                    while((count=input.read(buffer))!=-1){bytes+=count;if(bytes>20_000_000L||total+bytes>200_000_000L)throw new IOException("Photo storage limit");output.write(buffer,0,count);}
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Unsupported image");
                result.added.add(new NookStore.Photo(file.getName(),displayName(uri)));total+=file.length();accepted=true;
            }catch(IOException|SecurityException ignored){result.skipped++;}
            finally {if(!accepted)file.delete();}
        }
        return result;
    }
    private String displayName(Uri uri) {
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()){String name=cursor.getString(0);if(name!=null&&!name.isEmpty())return name.substring(0,Math.min(name.length(),100));}
        }catch(RuntimeException ignored){}
        return "Imported photo";
    }
    Bitmap decode(NookStore.Photo photo) {
        File file=new File(store.photoDirectory,photo.file);
        try {
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(bounds.outWidth/options.inSampleSize>1920||bounds.outHeight/options.inSampleSize>1920)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(bitmap==null)return null;
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try{orientation=new ExifInterface(file.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
            Matrix matrix=new Matrix();
            switch(orientation){
                case ExifInterface.ORIENTATION_ROTATE_90:matrix.postRotate(90);break;
                case ExifInterface.ORIENTATION_ROTATE_180:matrix.postRotate(180);break;
                case ExifInterface.ORIENTATION_ROTATE_270:matrix.postRotate(270);break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.postScale(1,-1);break;
                case ExifInterface.ORIENTATION_TRANSPOSE:matrix.postRotate(90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_TRANSVERSE:matrix.postRotate(270);matrix.postScale(-1,1);break;
                default:break;
            }
            return matrix.isIdentity()?bitmap:Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
        }catch(RuntimeException|OutOfMemoryError ignored){return null;}
    }
}
