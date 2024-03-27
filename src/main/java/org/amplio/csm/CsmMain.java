package org.amplio.csm;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Driver for the CSM Compiler / Decompiler.
 */
public class CsmMain {
    public static final String version = "2.0";
    public static final int sVersion = 2;

    public final boolean verbose;
    public final Namespace parsedArgs;

    public static void main(String[] args) throws Exception {
        Namespace parsedArgs = parseArgs(args);
        String source_path = parsedArgs.getString("source");
        String output_path = parsedArgs.getString("output");

        if (StringUtils.isBlank(output_path)) {
            output_path = FilenameUtils.removeExtension(source_path) + ".csm";
        }

        if (!parsedArgs.getBoolean("quiet")) {
            System.out.println("CsmMain version: " + version);
        }

        CsmMain main = new CsmMain(parsedArgs);
        if (parsedArgs.getBoolean("decompile")) {
            main.decompile(source_path);
        } else {
            main.compile(source_path, output_path);
        }
    }

    public CsmMain(Namespace parsedArgs) {
        this.parsedArgs = parsedArgs;
        this.verbose = parsedArgs.getBoolean("verbose");
    }

    /**
     * Compile the input yaml file to the output csm binary.
     * @param source_path to read
     * @param output_path to write
     */
    private void compile(String source_path, String output_path) {
        File source = new File(source_path);
        File output = new File(output_path);

        InputStream input;
        try {
            input = new FileInputStream(source);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(options);
        Object data = yaml.load(input);

        //noinspection unchecked
        Compiler compiler = new Compiler((Map<String,Object>)data);
        CsmData csmData = compiler.go();

        if (csmData == null) {
            // Compile-time errors.
            List<String> errors = new ArrayList<>();
            compiler.fillErrors(errors);
            System.out.printf("Error(s) compiling script:\n%s\n", String.join("\n    ", errors));
        } else {
            // Compiler detected no errors. Does the CsmData detect anything further? (Shouldn't; same checks.)
            if (csmData.isValid()) {
                // All is good, write the csm.
                try (FileOutputStream fos = new FileOutputStream(output)) {
                    // Here we write the binary CSM data.
                    CsmWriter csmWriter = new CsmWriter(csmData, fos, verbose);
                    csmWriter.emit();
                    if (!parsedArgs.getBoolean("quiet")) {
                        System.out.printf("Use 'xxd -i %s' to create a C include file.\n", output_path);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                List<String> errors = new ArrayList<>();
                csmData.fillErrors(errors);
                System.out.printf("Error(s) in script:\n%s\n", String.join("\n    ", errors));
            }
        }
        if (parsedArgs.getBoolean("list") && csmData != null) {
            // There may be errors in the script, but try to print it anyway.
            System.out.println(csmData);
        }
    }

    /**
     * Decompile the given csm input. Output to stdout.
     * @param source_path of the csm input file.
     * @throws IOException if the csm can't be written.
     */
    private void decompile(String source_path) throws IOException {
        File source = new File(source_path);

        Decompiler decompiler = new Decompiler(source);
        decompiler.read();
        CsmData csmData = decompiler.getCsmData();
        csmData.asYaml = false;
        System.out.println(csmData);
    }

    /**
     * Define and parse arguments for the main program.
     *
     * @param args from the command line.
     * @return a "Namespace" object with values for all of the arguments.
     */
    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("CsmMain")
                                               .build()
                                               .defaultHelp(true)
                                               .description(
                                                   "Compile text control file to .csm file.");
        parser.addArgument("source").required(true).help("Input control file.");
        parser.addArgument("--output").required(false).help("Output .csm file. Default ${input}.csm");
        parser.addArgument("--list", "-l").required(false).help("Create a YAML listing of the script").action(Arguments.storeTrue());
        parser.addArgument("--verbose", "-v").action(Arguments.storeTrue());
        parser.addArgument("--quiet", "-q").action(Arguments.storeTrue());
        parser.addArgument("--decompile", "-d").action(Arguments.storeTrue());

        Namespace namespace = null;
        try {
            namespace = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return namespace;
    }

}
