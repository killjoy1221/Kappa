package mnm.mods.kappa.blocks;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes("mnm.mods.kappa.blocks.BlockDef")
public class BlockModelGen extends AbstractProcessor {

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Messager messager;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                process(annotation, element);
            }
        }
        return true;
    }

    private void process(TypeElement annotation, Element element) {
        BlockDef block = element.getAnnotation(BlockDef.class);
        String namespace = block.namespace();
        String blockname = block.blockname();
        for (Type s : Type.values()) {
            try {
                FileObject file;
                switch (s) {
                case BLOCK_STATE:
                    file = filer.createResource(StandardLocation.CLASS_OUTPUT,
                            s.getPackage(namespace), blockname + ".json", element);
                    fillBlockState(block, file);
                    break;
                case BLOCK_MODEL:
                    for (int i = 0; i < block.variants().length; i++) {
                        BlockVariant var = block.variants()[i];
                        if (!var.createModel()) {
                            continue;
                        }
                        String model = var.modelName();
                        if (model.isEmpty()) {
                            model = blockname + "_" + i;
                        }
                        try {
                            file = filer.createResource(StandardLocation.CLASS_OUTPUT, s.getPackage(namespace),
                                    model + ".json", element);
                            fillBlockModel(block, var, file);
                        } catch (IllegalArgumentException e) {
                            messager.printMessage(Kind.MANDATORY_WARNING, e.getMessage(), element);
                        }
                    }
                    break;
                case ITEM_MODEL:
                    for (int i = 0; i < block.variants().length; i++) {
                        BlockVariant var = block.variants()[i];
                        if (!var.createItem()) {
                            continue;
                        }
                        String model = var.modelName();
                        if (model.isEmpty()) {
                            model = blockname + "_" + i;
                        }
                        file = filer.createResource(StandardLocation.CLASS_OUTPUT, s.getPackage(namespace),
                                model + ".json", element);
                        fillItemModel(model, block.namespace(), file);
                    }
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void fillBlockState(BlockDef blockDef, FileObject file) throws IOException {
        Writer writer = null;
        try {
            writer = file.openWriter();
            writer.write(gson.toJson(new Blockstate(blockDef)));
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // silence
                }
            }
        }
    }

    private void fillBlockModel(BlockDef block, BlockVariant var, FileObject file) throws IOException {
        Writer writer = null;
        String[] params = var.modelType().arguments;
        String[] args = var.textures();
        if (args.length == 0) {
            args = new String[] { block.blockname() };
        }
        if (params.length != args.length) {
            throw new IllegalArgumentException(var.modelType() + " requires " + params.length + " arguments."
                    + " Given " + args.length);
        }
        try {
            writer = file.openWriter();
            writer.write(gson.toJson(new Model(var.modelType().toString(), block.namespace(), params, args)));
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // silence
                }
            }
        }
    }

    private void fillItemModel(String name, String namespace, FileObject file) throws IOException {
        Writer writer = null;
        try {
            writer = file.openWriter();
            writer.write(gson.toJson(new Model(namespace, name)));
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // silence
                }
            }
        }
    }

    private enum Type {

        BLOCK_MODEL("assets.%s.models.block"),
        BLOCK_STATE("assets.%s.blockstates"),
        ITEM_MODEL("assets.%s.models.item");

        private String pkg;

        private Type(String pkg) {
            this.pkg = pkg;
        }

        public String getPackage(String s) {
            return String.format(pkg, s);
        }
    }

    // Json classes

    @SuppressWarnings("unused")
    private class Blockstate {
        Map<String, Model> variants = new HashMap<String, Model>();

        private Blockstate(BlockDef block) {
            BlockVariant[] states = block.variants();
            if (states.length == 0) {
                variants.put("normal", new Model(block.namespace() + ":" + block.blockname()));
                return;
            }
            for (int i = 0; i < block.variants().length; i++) {
                BlockVariant var = block.variants()[i];
                String variant = var.variant();
                String model = var.modelName();
                if (model.isEmpty()) {
                    model = block.blockname() + "_" + i;
                }
                variants.put(variant, new Model(block.namespace() + ":" + model));
            }
        }

        private class Model {
            String model;

            private Model(String model) {
                this.model = model;
            }
        }
    }

    @SuppressWarnings("unused")
    private class Model {
        String parent;
        Map<String, String> textures;
        Map<String, Display> display;

        private Model(String namespace, String parent) {
            this.parent = namespace + ":block/" + parent;
            this.display = new HashMap<>();
            this.display.put("thirdperson", new Display());
        }

        private Model(String parent, String namespace, String[] params, String[] args) {
            this.parent = "block/" + parent;
            this.textures = new HashMap<String, String>();
            for (int i = 0; i < params.length; i++) {
                textures.put(params[i], namespace + ":blocks/" + args[i]);
            }
        }

        private class Display {
            double[] rotation = { 10, -45, 170 };
            double[] translation = { 0, 1.5, -2.75 };
            double[] scale = { 0.375, 0.375, 0.375 };
        }
    }

}
