package reactors.examples;

import java.util.ArrayList;
import java.util.List;
import prerna.ds.py.PyTranslator;
import prerna.sablecc2.om.PixelDataType;
import prerna.sablecc2.om.ReactorKeysEnum;
import prerna.sablecc2.om.nounmeta.NounMetadata;
import prerna.util.AssetUtility;
import prerna.util.Constants;
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
    int inputNum = (Integer) this.keyValue.get(ReactorKeysEnum.NUMERIC_VALUE.getKey());
    String appFolder = AssetUtility.getProjectAssetsFolder(projectId);
    // define the file to grab the helper function from
    String sourceFile = "nthFibonacci.py";
    // define the helper function to be executed
    String functionName = "nthFibonacci";
    String path = appFolder + DIR_SEPARATOR + Constants.PY_BASE_FOLDER + DIR_SEPARATOR;
    path = path.replace("\\", "/");

    String moduleName = sourceFile.replace(".py", "");

    // define the arguments to be passed to the function
    List<Object> argsList = new ArrayList<>();
    argsList.add(inputNum);

    String args = "";
    if (argsList != null && !argsList.isEmpty()) {
      args = String.join(", ", argsList);
    }

    // Create a python script to be executed
    String commands =
        "import sys\n"
            + "sys.path.append(\""
            + path
            + "\")\n"
            + "from "
            + moduleName
            + " import "
            + functionName
            + "\n"
            + functionName
            + "("
            + args
            + ")\n";

    PyTranslator pt = this.insight.getPyTranslator();
    Object pyResponse = pt.runDirectPy(this.insight, commands);

    return new NounMetadata(pyResponse, PixelDataType.CONST_STRING);
  }
}
