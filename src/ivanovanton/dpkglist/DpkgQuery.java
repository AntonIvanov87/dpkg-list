package ivanovanton.dpkglist;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static ivanovanton.dpkglist.DpkgListMain.execSh;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

final class DpkgQuery {
  private static final int batchSize = 100;
  private static final String packageName = "Package: ";
  private static final String installedSize = "Installed-Size: ";
  private static final String replaces = "Replaces: ";
  private static final String provides = "Provides: ";
  private static final String depends = "Depends: ";
  private static final Pattern commaSpacePattern = Pattern.compile(", ");
  private static final Pattern dependPattern = Pattern.compile("( |:)");

  static Set<Package> getPackages() throws IOException {
    try (BufferedReader dpkgQueryReader = execSh("dpkg-query -l | grep '^ii' | awk '{print $2}'")) {
      Set<Package> allPackages = new HashSet<>();
      Set<String> batchOfPackagesNames = new HashSet<>(batchSize);
      while (true) {
        String dpkgQueryLine = dpkgQueryReader.readLine();
        if (dpkgQueryLine != null) {
          batchOfPackagesNames.add(dpkgQueryLine);
        }
        if (batchOfPackagesNames.size() == batchSize || dpkgQueryLine == null) {
          allPackages.addAll(packagesFromNames(batchOfPackagesNames));
          batchOfPackagesNames.clear();
        }
        if (dpkgQueryLine == null) {
          break;
        }
      }
      return allPackages;
    }
  }

  private static Set<Package> packagesFromNames(Set<String> packagesNames) throws IOException {
    if (packagesNames.isEmpty()) {
      return emptySet();
    }
    try (BufferedReader dpkgQueryReader = execSh("dpkg-query --status " + String.join(" ", packagesNames))) {
      Set<Package> packages = new HashSet<>(packagesNames.size());
      Package currentPackage = null;
      while (true) {
        String packageLine = dpkgQueryReader.readLine();
        if (packageLine == null || packageLine.trim().isEmpty()) {
          if (currentPackage != null) {
            checkPackage(currentPackage);
            packages.add(currentPackage);
            currentPackage = null;
          }
        }

        if (packageLine == null) {
          break;
        }

        if (packageLine.startsWith(packageName)) {
          currentPackage = new Package(packageLine.substring(packageName.length()));

        } else if (packageLine.startsWith(installedSize)) {
          String sizeStr = packageLine.substring(installedSize.length());
          currentPackage.size = parseInt(sizeStr);

        } else if (packageLine.startsWith(replaces)) {
          currentPackage.replaces = parseReplaces(packageLine);

        } else if (packageLine.startsWith(provides)) {
          currentPackage.provides = parseProvides(packageLine);

        } else if (packageLine.startsWith(depends)) {
          currentPackage.depends = parseDepends(packageLine);

        }
      }
      return packages;
    }
  }

  private static void checkPackage(Package currentPackage) {
    if (currentPackage.size == 0) {
      throw new RuntimeException("Failed to find size of " + currentPackage.name);
    }
  }

  private static Set<String> parseReplaces(String replacesLine) {
    String replacesStr = replacesLine.substring(replaces.length());
    return Set.of(commaSpacePattern.split(replacesStr));
  }

  private static Set<String> parseProvides(String providesLine) {
    String providesStr = providesLine.substring(provides.length());
    return Set.of(commaSpacePattern.split(providesStr));
  }

  private static Set<String> parseDepends(String dependsLine) {
    String dependsStr = dependsLine.substring(depends.length());
    // TODO: |
    return stream(commaSpacePattern.split(dependsStr)).map(dependStr -> dependPattern.split(dependStr)[0]).collect(toSet());
  }

  final static class Package {
    private final String name;
    private int size;
    private Set<String> replaces = emptySet();
    private Set<String> provides = emptySet();
    private Set<String> depends = emptySet();

    private Package(String name) {
      this.name = name;
    }

    String getName() {
      return name;
    }

    int getSize() {
      return size;
    }

    Set<String> getReplaces() {
      return replaces;
    }

    Set<String> getProvides() {
      return provides;
    }

    Set<String> getDepends() {
      return depends;
    }

    @Override
    public String toString() {
      return "DpkgQueryPackage{" + name + '}';
    }
  }

  private DpkgQuery() {}
}
