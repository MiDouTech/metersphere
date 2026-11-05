package io.metersphere.agent.service;

import io.metersphere.sdk.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BugImageLocalGenerationTests {
    @TempDir Path directory;
    Path objectDirectory(){return directory.resolve("project/p/file-management");}
    FileRequest request(){return new FileRequest(objectDirectory().toString(),"LOCAL","proof.png");}
    @Test void everySdkWriteGetsANewDurableGenerationEvenWithIdenticalBytesAndRestoredTime() throws Exception {
        byte[] bytes=BugImageValidatorTests.image("png",2,2);Files.createDirectories(objectDirectory());Path path=objectDirectory().resolve("proof.png");Files.write(path,bytes);
        var repository=new LocalFileRepository();var request=request();var time=Files.getLastModifiedTime(path);
        String version=repository.objectRevision(request);assertFalse(Files.exists(objectDirectory().resolve(".ms-object-generations")),"A legacy read must not write metadata");
        for(int mode=0;mode<3;mode++){
            if(mode==0)repository.saveFile(bytes,request);
            else if(mode==1)repository.saveFile(new ByteArrayInputStream(bytes),request);
            else repository.saveFile(new MockMultipartFile("file","proof.png","image/png",bytes),request);
            Files.setLastModifiedTime(path,time);String next=repository.objectRevision(request);
            assertNotEquals(version,next);assertArrayEquals(bytes,Files.readAllBytes(path));
            assertEquals(next,new LocalFileRepository().objectRevision(request));version=next;
        }
    }
    @Test void partialWriteIsNeverCertifiedAndConcurrentWritersAreSerialized() throws Exception {
        byte[] bytes=BugImageValidatorTests.image("png",2,2);var repository=new LocalFileRepository();repository.saveFile(bytes,request());
        String original=repository.objectRevision(request());var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var stream=new ByteArrayInputStream(bytes){
            @Override public synchronized int read(byte[] target,int offset,int length){
                entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Writer release timed out");}
                catch(InterruptedException failure){Thread.currentThread().interrupt();throw new RuntimeException(failure);}
                return super.read(target,offset,length);
            }
        };
        try(var workers=Executors.newFixedThreadPool(2)){
            var first=workers.submit(()->repository.saveFile(stream,request()));assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertThrows(IOException.class,()->repository.objectRevision(request()));
            try(var center=org.mockito.Mockito.mockStatic(FileCenter.class)){
                center.when(()->FileCenter.getRepository("LOCAL")).thenReturn(repository);
                var store=new AgentBugImageStore(org.mockito.Mockito.mock(io.metersphere.project.service.FileManagementService.class));
                assertThrows(IOException.class,()->store.head(request()),"An in-progress write must not be mistaken for a complete non-image attachment index");
            }
            var second=workers.submit(()->new LocalFileRepository().saveFile(bytes,request()));
            assertThrows(TimeoutException.class,()->second.get(200,TimeUnit.MILLISECONDS));
            release.countDown();first.get(5,TimeUnit.SECONDS);second.get(5,TimeUnit.SECONDS);
            assertNotEquals(original,repository.objectRevision(request()));assertArrayEquals(bytes,Files.readAllBytes(objectDirectory().resolve("proof.png")));
        } finally {release.countDown();}
    }
    @Test void failedWriterLeavesAnUnreadableGenerationUntilAnExplicitSuccessfulWrite() throws Exception {
        var repository=new LocalFileRepository();byte[] bytes=BugImageValidatorTests.image("png",2,2);repository.saveFile(bytes,request());
        String before=repository.objectRevision(request());var broken=new InputStream(){public int read()throws IOException{throw new IOException("controlled writer failure");}};
        assertThrows(IOException.class,()->repository.saveFile(broken,request()));assertThrows(IOException.class,()->repository.objectRevision(request()));
        repository.saveFile(bytes,request());assertNotEquals(before,repository.objectRevision(request()));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void caseAliasesUseTheSameGenerationOnWindows() throws Exception {
        var repository=new LocalFileRepository();byte[] bytes=BugImageValidatorTests.image("png",2,2);repository.saveFile(bytes,request());
        String before=repository.objectRevision(request());var time=Files.getLastModifiedTime(objectDirectory().resolve("proof.png"));
        repository.saveFile(bytes,new FileRequest(objectDirectory().toString().toUpperCase(java.util.Locale.ROOT),"LOCAL","PROOF.PNG"));Files.setLastModifiedTime(objectDirectory().resolve("proof.png"),time);
        assertNotEquals(before,repository.objectRevision(request()));
    }
    public static class WriterProcess {
        public static void main(String[] args) throws Exception {
            var repository=new LocalFileRepository();var request=new FileRequest(args[0],"LOCAL","proof.png");
            var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
            if(args[1].equals("hold")){
                var once=new java.util.concurrent.atomic.AtomicBoolean();
                var stream=new ByteArrayInputStream(bytes.toByteArray()){
                    @Override public synchronized int read(byte[] target,int offset,int length){
                        if(once.compareAndSet(false,true)){
                            System.out.println("WRITER_READY");System.out.flush();
                            try{new BufferedReader(new InputStreamReader(System.in)).readLine();}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
                        }
                        return super.read(target,offset,length);
                    }
                };repository.saveFile(stream,request);
            }else{System.out.println("WRITE_STARTED");System.out.flush();repository.saveFile(bytes.toByteArray(),request);}
        }
    }
    private Process child(String mode) throws Exception {
        String cp=System.getProperty("java.class.path").replace('\\','/');
        Path args=directory.resolve(mode+".args");Files.writeString(args,"--class-path\n\""+cp+"\"\n"+WriterProcess.class.getName()+"\n\""+objectDirectory().toString().replace('\\','/')+"\"\n"+mode+"\n");
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(),"@"+args).redirectErrorStream(true).start();
    }
    private void awaitOutput(Process process,String expected) throws Exception {
        var line=CompletableFuture.supplyAsync(()->{
            try{var reader=new BufferedReader(new InputStreamReader(process.getInputStream()));String value;
                while((value=reader.readLine())!=null)if(value.equals(expected))return value;return "PROCESS_EXITED";
            }catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
        });assertEquals(expected,line.get(10,TimeUnit.SECONDS));
    }
    @Test void operatingSystemLockCoordinatesTwoActualWriterJvms() throws Exception {
        var repository=new LocalFileRepository();repository.saveFile(BugImageValidatorTests.image("png",2,2),request());
        Process holder=child("hold"),writer=null;
        try{
            awaitOutput(holder,"WRITER_READY");writer=child("write");awaitOutput(writer,"WRITE_STARTED");
            assertFalse(writer.waitFor(250,TimeUnit.MILLISECONDS));assertThrows(IOException.class,()->repository.objectRevision(request()));
            new PrintWriter(holder.getOutputStream(),true).println("release");
            assertTrue(holder.waitFor(10,TimeUnit.SECONDS));assertTrue(writer.waitFor(10,TimeUnit.SECONDS));
            assertEquals(0,holder.exitValue());assertEquals(0,writer.exitValue());assertNotNull(repository.objectRevision(request()));
        }finally{
            holder.destroyForcibly();if(writer!=null)writer.destroyForcibly();
            holder.waitFor(3,TimeUnit.SECONDS);if(writer!=null)writer.waitFor(3,TimeUnit.SECONDS);
        }
    }
    @Test void unrelatedLocalPluginStorageDoesNotAcquireGenerationSidecars() throws Exception {
        Path plugins=directory.resolve("plugins");Files.createDirectories(plugins);
        new LocalFileRepository().saveFile(new byte[]{1,2,3},new FileRequest(plugins.toString(),"LOCAL","fixture.jar"));
        assertFalse(Files.exists(plugins.resolve(".ms-object-generations")));try(var entries=Files.list(plugins)){assertEquals(1,entries.count());}
    }
}
