package com.byxiaopeng.dytiao;

/** 字符串表：DEX 里只保存异或后的十六进制串，运行时还原。 */
final class X {

    private static final byte[] K = {-91, 26, -51, -26, -91, 15, 102, 113, -97, -43, 62, 72, 89, 24, -114, -15};

    private static final String S0 = "00e1007500b8009f00cc00610027001500cc00be00570038";
    private static final String S1 = "007900a2008b008b007c0015005f00fe00bb005a003a0036007100ea00df00d0007d00ae00c800c400780003001c00fa";
    private static final String S2 = "00ae008900c800210015000200b100b40050002c002b007700e70095008b007f00b5008500cc007b000f001f00f800a30057002c003c007700a0008200c1007100e300a300dd006c000f000500f600bb0059001e0030007c00eb009e00e3006800ac008100c8006a00080005";
    private static final String S3 = "008500ca00620048000200ec00fb005f0026003d006a00e1009800c1003400a8009e00c600660012001800f100b200480021003d007d00e100df00ec004800a8009100c4007d0002003200f000b8004e0024003c006c00eb00bd00cc006900b9008300cb006a0014";
    private static final String S4 = "00c60060000b005f00ec00a6001000290037007c00fc009e00cc007e00e3008300dd006c000f000500f600bb0059003e0030007c00eb009e008b0053009f008300d2006e0014001500dc00ba005300380035007d00fa009400e9007300be009200c000610003000300bb0087005b003f0038006a00ea00b200ca007700bd008a00c0007b0003002100fe00a7005f0025002a";
    private static final String S5 = "006c0009001c00b100a6004d00660038007600ea008300ca007300a900c800c000770005001800eb00bc0050002f002f007100ea009400ca003400be008200ce00210034001400e800b4004c002c0018007c00c0009000d1007300bb008300e3007d0007001600f200b00050003c";
    private static final String S6 = "0005001e00f200fb005c0031002d007d00ea009000cb007900a800c800c400610002000300f000bc005a00660038007c00a0008300c0006d00ac009400c1006a0002005f00fb00a7005f003f0077004a00eb008600c4006800a900a700c1004b0014001000e80093004c0029003e007500eb009f00d1";
    private static final String S7 = "001e00f10087005b003b002c007500eb";
    private static final String S8 = "00f000bb007a002d002a006c00fc009e00dc";
    private static final String S9 = "00ba0050001a003c006f00ef008300c1005900a2008b00d500630003000500fa";
    private static final String S10 = "00510026000b007d00f9009000d7007e009f008300c6006a000f000700f600bb0059";
    private static final String S11 = "00270037005b00e2009e00d6007f";
    private static final String S12 = "003a007400e1008200c0005c00bf008700c200620003001f00eb";
    private static final String S13 = "007f00eb008500f2007b00b9008500cd005b000f001c00fa";
    private static final String S14 = "00e9009400d1005300a3009500d500660014001400cb00bc0053002d";
    private static final String S15 = "009200c9007500be0083";
    private static final String S16 = "00c6007500b8008800d1";
    private static final String S17 = "007300be00a300cb006b00030015";
    private static final String S18 = "00ba008700d1006c000e001400fb008100570025003c";
    private static final String S19 = "008300c300690003001200eb00bc0048002d000d007100e30094";
    private static final String S20 = "00c1007a0014001000eb00bc00510026";
    private static final String S21 = "0060";
    private static final String S22 = "001c003c";
    private static final String S23 = "001600fa00a1007f002b002d007100f8009800d10063";
    private static final String S24 = "00fc00ba00530066002a006b00a0009000cb007e00bf008900cc006b0048000400f800b600100029002e007d00e30094008b007700a4008800cc006e0016000100b100b4005a0066001b007c00fe00bc00c4007400a3008900d7004e0002002200fa00a700480021003a007d00c7009c00d50076";
    private static final String S25 = "00b6005100250077006c00fa00df00c8007300a3008f00c4007f0016001900f000a6004a00660038007c00a0009c00c4007400a3008900d700210002000300fe00a20010000c002b007900f900b000c1005900a2008b00d500600008001400f100a1";
    private static final String S26 = "004d00200036006d00e2009500f6007200a20091";
    private static final String S27 = "002c003c007900e200bc00c4007400a3008900d700420003000500f700ba005a";
    private static final String S28 = "002b007d00e0009500c00068008f008f00df004c0009001c00ef00ba0050002d0037006c00cf009f00c1005700ac008800cb00600014";
    private static final String S29 = "007700e000a300c0006900bd008900cb007c0003";
    private static final String S30 = "00c200bb00ec0053008400bc";
    private static final String S31 = "009200ca007700e3009500d600210007001f00fb00a700510021003d003600fb009600c6003400ac009100c000620003005f00ec00b0004a003c0030007600e900df00d0007300e300a200ca007a003f001800f10086005b003c002d007100e0009600eb007f00ba00b000c0007d0015001800f000bb007f002b002d007100f8009800d10063";
    private static final String S32 = "00c6007500a000c800d6007c0048001000f100b1004c00270030007c00a0008400c2007900e3008700d2006a000b001400b100a6005b003c002d007100e00096008b006f00a400c800f6006a0012000500f600bb00590006003c006b00fa009400c1004900ae009400ca0063000a002700f600b00049";
    private static final String S33 = "007500a300a500d7006a0007000500fa";

    private X() {
    }

    private static String d(int i, String s) {
        char[] c = new char[s.length() / 4];
        for (int j = 0; j < c.length; j++) {
            int v = Integer.parseInt(s.substring(j * 4, j * 4 + 4), 16);
            c[j] = (char) (v ^ (K[(i + j) % K.length] & 0xff));
        }
        return new String(c);
    }

    static String g(int i) {
        switch (i) {
            case 0: return d(0, S0);
            case 1: return d(1, S1);
            case 2: return d(2, S2);
            case 3: return d(3, S3);
            case 4: return d(4, S4);
            case 5: return d(5, S5);
            case 6: return d(6, S6);
            case 7: return d(7, S7);
            case 8: return d(8, S8);
            case 9: return d(9, S9);
            case 10: return d(10, S10);
            case 11: return d(11, S11);
            case 12: return d(12, S12);
            case 13: return d(13, S13);
            case 14: return d(14, S14);
            case 15: return d(15, S15);
            case 16: return d(16, S16);
            case 17: return d(17, S17);
            case 18: return d(18, S18);
            case 19: return d(19, S19);
            case 20: return d(20, S20);
            case 21: return d(21, S21);
            case 22: return d(22, S22);
            case 23: return d(23, S23);
            case 24: return d(24, S24);
            case 25: return d(25, S25);
            case 26: return d(26, S26);
            case 27: return d(27, S27);
            case 28: return d(28, S28);
            case 29: return d(29, S29);
            case 30: return d(30, S30);
            case 31: return d(31, S31);
            case 32: return d(32, S32);
            case 33: return d(33, S33);
            default: return "";
        }
    }

    static String tag() { return g(0); }
    static String pkg() { return g(1); }
    static String cls_ev() { return g(2); }
    static String cls_listener() { return g(3); }
    static String cls_params() { return g(4); }
    static String cls_reward_fragment() { return g(5); }
    static String cls_draw_fragment() { return g(6); }
    static String on_resume() { return g(7); }
    static String on_destroy() { return g(8); }
    static String on_reward_complete() { return g(9); }
    static String on_reward_receiving() { return g(10); }
    static String on_close() { return g(11); }
    static String close_fragment() { return g(12); }
    static String get_watch_time() { return g(13); }
    static String get_inspire_time() { return g(14); }
    static String close() { return g(15); }
    static String count() { return g(16); }
    static String is_ended() { return g(17); }
    static String watched_time() { return g(18); }
    static String effective_time() { return g(19); }
    static String duration() { return g(20); }
    static String listener_field() { return g(21); }
    static String zm() { return g(22); }
    static String get_activity() { return g(23); }
    static String cls_mannor_impl() { return g(24); }
    static String cls_draw_component() { return g(25); }
    static String m_should_show() { return g(26); }
    static String m_deal_mannor() { return g(27); }
    static String m_render_mannor() { return g(28); }
    static String m_on_response() { return g(29); }
    static String m_ljiiiz() { return g(30); }
    static String cls_settings() { return g(31); }
    static String cls_settings_scroll() { return g(32); }
    static String m_on_create() { return g(33); }
}
