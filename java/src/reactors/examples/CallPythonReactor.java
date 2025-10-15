package reactors.examples;

import java.util.ArrayList;
import java.util.List;
import prerna.ds.py.PyTranslator;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import reactors.AbstractProjectReactor;

/**
 * Example reactor that calls Python functions from Java. Takes a numeric input and calls
 * nthFibonacci Python function.
 */
public class CallPythonReactor extends AbstractProjectReactor {

  // Note: Has access to protected variables defined in AbstractProjectReactor

  public CallPythonReactor() {

    // list of keys the reactor is expecting
    this.keysToGet = new String[] {ReactorKeysEnum.NUMERIC_VALUE.getKey()};

    // 1 for required keys, 0 for optional
    this.keyRequired = new int[] {1};
  }

  @Override
  protected NounMetadata doExecute() {
    // grab the input number
    String inputNum = this.keyValue.get(ReactorKeysEnum.NUMERIC_VALUE.getKey());

    // define the file to grab the helper function from
    String sourceFile = "nthFibonacci.py";

    PyTranslator pt = this.insight.getPyTranslator();

    String projectId = this.insight.getContextProjectId();
    if (projectId == null) {
      projectId = this.insight.getProjectId();
    }

    String fibonacciModule = pt.loadPythonModuleFromFile(this.insight, sourceFile, projectId);

    String functionName = "nthFibonacci";

    // define the arguments to be passed to the function
    List<Object> argsList = new ArrayList<>();
    argsList.add(inputNum);

    Object pyResponse =
        pt.runFunctionFromLoadedModule(this.insight, fibonacciModule, functionName, argsList);

    return new NounMetadata(pyResponse, PixelDataType.CONST_STRING);
  }
}
