package com.theclub.eventservice.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeServiceTest {

    @Test
    void pngDecodesBackToTicketPayload() throws Exception {
        String code = TicketIssuer.newTicketCode();
        byte[] png = new QrCodeService().ticketPng(code);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        String decoded = new QRCodeReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))))
                .getText();

        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(decoded).isEqualTo("THECLUB-TICKET:" + code);
        assertThat(QrCodeService.extractCode(decoded)).isEqualTo(code);
        assertThat(QrCodeService.extractCode("  " + code + " ")).isEqualTo(code);
    }
}
