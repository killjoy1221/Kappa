package mnm.mods.kappa.mixin;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.spongepowered.asm.launch.MixinTweaker;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Annotation processor for Mixins which generates the resulting class files.
 *
 * @author Matthew Messinger
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes("org.spongepowered.asm.mixin.Mixin")
public class MixinSourcifier extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        setupLaunch();
        new MixinTweaker().injectIntoClassLoader(Launch.classLoader);
    }

    /**
     * Sets up {@link Launch} without calling its main method.
     */
    private void setupLaunch() {

        final URLClassLoader ucl = (URLClassLoader) getClass().getClassLoader();
        Launch.classLoader = new LaunchClassLoader(ucl.getURLs());
        Launch.blackboard = new HashMap<String, Object>();
        Thread.currentThread().setContextClassLoader(Launch.classLoader);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement ele : annotations) {
            process(roundEnv.getElementsAnnotatedWith(ele));
        }
        return false;
    }

    private void process(Set<? extends Element> set) {
        for (Element ele : set) {
            process(ele);
        }
    }

    private void process(Element element) {
        Mixin mixin = element.getAnnotation(Mixin.class);
        List<? extends TypeMirror> mirrors;
        try {
            mixin.value();
            throw new RuntimeException(); // don't get past this
        } catch (MirroredTypesException e) {
            mirrors = e.getTypeMirrors();
        }
        for (TypeMirror mirror : mirrors) {
            Writer writer = null;
            try {
                // TODO decompile
                JavaFileObject file = this.processingEnv.getFiler().createClassFile(mirror.toString(), element);
                byte[] bytes = applyMixin(mirror, (TypeElement) element);
                writeClass(file, bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeQuietly(writer);
            }
        }
    }

    /**
     * Applies a Mixin
     *
     * @param clas The class to transform
     * @param mixin The mixin annotated class
     * @return Bytes of the new class
     * @throws IOException If an IOException occurs
     */
    private byte[] applyMixin(TypeMirror clas, TypeElement mixin) throws IOException {
        byte[] bytes = Launch.classLoader.getClassBytes(clas.toString());

        return bytes;
    }

    private void writeClass(FileObject file, byte[] bytes) throws IOException {

        OutputStream writer = null;
        try {
            writer = file.openOutputStream();
            for (byte b : bytes) {
                writer.write(b);
            }
            writer.flush();
        } finally {
            closeQuietly(writer);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            // quiet
        }
    }
}
