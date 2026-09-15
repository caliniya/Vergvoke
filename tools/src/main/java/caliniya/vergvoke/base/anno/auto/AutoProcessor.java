package caliniya.vergvoke.base.anno.auto;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.Writer;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("caliniya.vergvoke.base.anno.auto.AnnoProc")
public class AutoProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> processorClasses =
            roundEnv.getElementsAnnotatedWith(AnnoProc.class);

        if (processorClasses.isEmpty()) {
            return false;
        }

        List<String> processorNames = new ArrayList<>();

        for (Element element : processorClasses) {
            String className = ((TypeElement) element).getQualifiedName().toString();
            processorNames.add(className);
            messager.printMessage(Diagnostic.Kind.NOTE, "AnnotationProcessor: " + className);
        }

        generateServiceFile(processorNames);
        return true;
    }

    private void generateServiceFile(List<String> processorNames) {
        try {
            String path = "META-INF/services/javax.annotation.processing.Processor";
            Writer writer = filer.createResource(StandardLocation.CLASS_OUTPUT, "", path).openWriter();

            for (String name : processorNames) {
                writer.write(name + "\n");
            }
            writer.close();

        } catch (Exception e) {
            messager.printMessage(
                Diagnostic.Kind.ERROR, "Failed to generate SPI: " + e.getMessage());
        }
    }
}