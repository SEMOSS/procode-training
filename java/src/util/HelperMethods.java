package util;

import prerna.auth.User;

public class HelperMethods {

  public static String getUserId(User user) {
    return user.getPrimaryLoginToken().getId();
  }
}
