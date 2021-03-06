package gs.autopojo.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import gs.autopojo.POJO;
import gs.autopojo.processor.tasks.ProcessClassTask;
import gs.autopojo.processor.tasks.WriteGenClassTask;
import io.reactivex.Completable;
import io.reactivex.Single;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"gs.autopojo.POJO", "gs.autopojo.ExtraAnnotation", "gs.autopojo.ExtraAnnotations"})
public class POJOProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        LinkedList<Completable> tasks = new LinkedList<>();

        TypeElement pojoElement = processingEnv.getElementUtils().getTypeElement(POJO.class.getCanonicalName());

        process(roundEnv, roundEnv.getElementsAnnotatedWith(POJO.class), tasks);

        Completable.mergeDelayError(tasks)
                .blockingAwait();
        return true;
    }

    private void process(RoundEnvironment roundEnv, Set<? extends Element> elements, List<Completable> tasks) {
        for (Element element : elements) {
            switch (element.getKind()) {
                case INTERFACE:
                    tasks.add(scheduleTask((TypeElement) element));
                    break;

                case ANNOTATION_TYPE:
                    process(roundEnv, roundEnv.getElementsAnnotatedWith((TypeElement) element), tasks);
                    break;

                default:
                    processingEnv.getMessager()
                            .printMessage(Diagnostic.Kind.ERROR, "Not an interface", element);
            }
        }
    }

    private Completable scheduleTask(TypeElement element) {
        return Single.fromCallable(new ProcessClassTask(
                processingEnv.getTypeUtils(),
                processingEnv.getElementUtils(),
                element))
                .filter($ -> $.name.enclosingClassName() == null) // only write top-level classes
                .doOnSuccess($ -> $.typeSpec.addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", getClass().getCanonicalName())
                        .build()))
                .flatMapCompletable($ -> Completable.fromCallable(new WriteGenClassTask(processingEnv.getFiler(), $)))
                .doOnError($ -> processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.ERROR, $.toString(), element));
    }

}
