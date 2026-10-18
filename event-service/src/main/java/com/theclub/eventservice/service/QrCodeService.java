package com.theclub.eventservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class QrCodeService {

    public static final String PAYLOAD_PREFIX = "THECLUB-TICKET:";
    private static final int SIZE_PX = 300;

    public static String payload(String ticketCode) {
        return PAYLOAD_PREFIX + ticketCode;
    }

    /**
     * Accepts what a scanner reads (the full payload) or a manually typed bare code.
     */
    public static String extractCode(String scanned) {
        String value = scanned.trim();
        return value.startsWith(PAYLOAD_PREFIX) ? value.substring(PAYLOAD_PREFIX.length()).trim() : value;
    }

    public byte[] ticketPng(String ticketCode) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload(ticketCode), BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
                    Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("Could not encode QR code", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
