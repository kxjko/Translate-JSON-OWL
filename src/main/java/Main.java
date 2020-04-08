
public class Main {
    public static void main(String[] args) {
        String sourceFile = "goods-20200405.owl";
        String targetFile = "goods-20200405-translated.owl";
        Translate translate = new Translate();
        translate.translate(sourceFile, targetFile);
    }

}
