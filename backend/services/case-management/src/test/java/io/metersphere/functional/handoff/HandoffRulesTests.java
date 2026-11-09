package io.metersphere.functional.handoff;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HandoffRulesTests {
    @ParameterizedTest @ValueSource(strings={"http://example.test/hook","https://user:pw@example.test/hook","https://example.test/#fragment","https://example.test:0/hook","https://example.test:65536/hook","https://example.test/hook\r\nX:evil"})
    void unsafeSyntaxRejected(String value){assertThrows(IllegalArgumentException.class,()->HandoffHttpTransport.syntax(value));}
    @ParameterizedTest @ValueSource(strings={"127.0.0.1","0.0.0.0","10.1.2.3","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","198.18.1.1","192.0.2.1","203.0.113.1","224.0.0.1","255.255.255.255","::1","::","fc00::1","fe80::1","2001:db8::1","2002:7f00::1"})
    void nonPublicAddressesRejected(String value)throws Exception {assertFalse(HandoffHttpTransport.publicAddress(InetAddress.getByName(value)));}
    @Test void publicAddressAndExactQueryPreserved()throws Exception{assertTrue(HandoffHttpTransport.publicAddress(InetAddress.getByName("8.8.8.8")));String url="https://example.test/Path?b=x&a=y";assertEquals(url,HandoffHttpTransport.syntax("  "+url+"  ").toString());assertThrows(IllegalArgumentException.class,()->HandoffHttpTransport.syntax("https://example.test/"+"x".repeat(4096)));}
    @Test void retryAfterParsesAndRejectsOverflow(){long now=1_000_000;assertEquals(now+60000,HandoffHttpTransport.retryAfter("60",now));assertEquals(0,HandoffHttpTransport.retryAfter("bad",now));assertEquals(0,HandoffHttpTransport.retryAfter("999999999999999999999999999",now));assertEquals(10_000,HandoffWorker.backoff(1));assertEquals(30_000,HandoffWorker.backoff(2));assertEquals(60_000,HandoffWorker.backoff(3));}
    @Test void encryptedSecretsAreRandomAndBoundToProject(){var s=new HandoffSecrets(Base64.getEncoder().encodeToString(new byte[32]));String a=s.seal("p","url","secret"),b=s.seal("p","url","secret");assertNotEquals(a,b);assertEquals("secret",s.open("p","url",a));assertThrows(IllegalStateException.class,()->s.open("other","url",a));assertThrows(IllegalStateException.class,()->s.open("p","token",a));assertThrows(IllegalStateException.class,()->new HandoffSecrets("").seal("p","url","secret"));}
    @Test void businessKeyIsPlanScopedAndUnambiguous(){assertNotEquals(HandoffPolicy.businessKey("a","bc","d"),HandoffPolicy.businessKey("ab","c","d"));assertNotEquals(HandoffPolicy.businessKey("a","b","c"),HandoffPolicy.businessKey("a","b","d"));}
    static HandoffPolicy.Snapshot valid(){var source=Map.of("a",1L);var members=Map.of("a",new HandoffPolicy.Member(1,"PASS"));return new HandoffPolicy.Snapshot("PREPARED","COMPLETED",true,true,"g","g",1,1,1,false,source,members,members,"op","op");}
    @Test void realQualificationUsesMembersVersionsAndSourceAuthority(){
        var v=valid();assertTrue(HandoffPolicy.evaluate(v).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot("COMPLETED",v.reviewState(),true,true,"g","g",1,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),"ARCHIVED",true,true,"g","g",1,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),false,true,"g","g",1,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,false,"g","g",1,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","old",1,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",2,1,1,false,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",1,1,1,true,v.source(),v.current(),v.frozen(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",1,1,1,false,Map.of(),Map.of(),Map.of(),"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",1,1,1,false,Map.of("a",2L),v.current(),v.frozen(),"op","op")).eligible());
        var fail=Map.of("a",new HandoffPolicy.Member(1,"UN_PASS"));assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",1,1,1,false,v.source(),fail,fail,"op","op")).eligible());
        assertFalse(HandoffPolicy.evaluate(new HandoffPolicy.Snapshot(v.planState(),v.reviewState(),true,true,"g","g",1,1,1,false,v.source(),v.current(),v.frozen(),"new","op")).eligible());
    }
    @Test void mixedDnsAndRebindingAreRejectedWithoutConnect()throws Exception {
        var transport=new HandoffHttpTransport(){int calls;protected InetAddress[] lookup(String host)throws UnknownHostException{return ++calls==1?new InetAddress[]{InetAddress.getByName("8.8.8.8")}:new InetAddress[]{InetAddress.getByName("8.8.8.8"),InetAddress.getByName("127.0.0.1")};}};
        transport.validate("https://receiver.example.test/");var result=transport.post("https://receiver.example.test/",null,new byte[0]);assertEquals("UNSAFE_ADDRESS",result.error());assertFalse(result.retryable());
    }

}
