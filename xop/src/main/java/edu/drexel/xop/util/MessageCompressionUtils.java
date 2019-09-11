package edu.drexel.xop.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Utility class for compress and decompress byte arrays
 * Created by duc on 10/19/16.
 */

public class MessageCompressionUtils {

    public static byte[] compressBytes(byte[] msg){
        if( XOP.ENABLE.COMPRESSION ) {
            Deflater compressor = new Deflater();

            compressor.setLevel(Deflater.BEST_SPEED);
            compressor.setInput(msg);
            compressor.finish();

            ByteArrayOutputStream bos = new ByteArrayOutputStream(msg.length);

            byte[] buf = new byte[msg.length];
            while (!compressor.finished()) {
                int count = compressor.deflate(buf);
                bos.write(buf, 0, count);
            }
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] compressedData = bos.toByteArray();
            return compressedData;
        }
        return msg;
    }

    public static byte[] decompressBytes(byte[] compressedData){
        if( XOP.ENABLE.COMPRESSION ) {
            Inflater decompressor = new Inflater();
            decompressor.setInput(compressedData);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(compressedData.length);
            byte[] buf = new byte[compressedData.length * 100];
            try {
                while (!decompressor.finished()) {
                    int count = decompressor.inflate(buf);
                    bos.write(buf, 0, count);
                }
                bos.close();
            } catch (DataFormatException | IOException e) {
                e.printStackTrace();
            }
            byte[] decompressedData = bos.toByteArray();
            return decompressedData;
        }
        return compressedData;
    }
}
