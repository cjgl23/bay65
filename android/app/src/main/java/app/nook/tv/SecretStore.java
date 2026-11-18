package app.nook.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Only ciphertext is persisted. The wrapping key stays in Android Keystore. */
final class SecretStore {
    // The LTA key keeps its original storage names so existing installations stay connected.
    private static final String ALIAS="nook.lta.v1";
    private final String alias;
    private final SharedPreferences preferences;
    SecretStore(Context c) {preferences=c.getSharedPreferences("nook_secret",Context.MODE_PRIVATE);alias=ALIAS;}
    private SecretStore(SharedPreferences preferences,String alias) {this.preferences=preferences;this.alias=alias;}
    /** A separate encrypted slot for the BAY65 Assistant's provider key. */
    static SecretStore assistant(Context c) {return new SecretStore(c.getSharedPreferences("nook_agent_secret",Context.MODE_PRIVATE),"nook.agent.v1");}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    synchronized void save(String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        String encrypted=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!preferences.edit().putString("encrypted",iv+":"+encrypted).commit())throw new IllegalStateException("Cannot save key");
    }
    synchronized String read() {
        String saved=preferences.getString("encrypted",null);if(saved==null)return null;
        try {
            String[] parts=saved.split(":",2);if(parts.length!=2)return null;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch(Exception unavailable) {return null;}
    }
    synchronized void clear() {
        preferences.edit().clear().commit();
        try {KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(alias);}catch(Exception ignored){}
    }
}
