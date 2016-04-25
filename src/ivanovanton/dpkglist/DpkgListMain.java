package ivanovanton.dpkglist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.Comparator.comparingInt;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public final class DpkgListMain {

  public static void main(String[] args) throws IOException {
    Set<DpkgQuery.Package> dpkgQueryPackages = DpkgQuery.getPackages();

    Set<RichPackage> richPackages = getRichPackages(dpkgQueryPackages);

    printResults(richPackages);
  }

  static BufferedReader execSh(String command) throws IOException {
    String[] listInstalledCmd = {"/bin/sh", "-c", command};
    Process process = Runtime.getRuntime().exec(listInstalledCmd);
    return new BufferedReader(new InputStreamReader(process.getInputStream()));
  }

  private static final class RichPackage {
    private final String name;
    private final int ownSize;
    private Set<RichPackage> depends = emptySet();
    private int sizeWithDeps;

    private RichPackage(String name, int ownSize) {
      this.name = name;
      this.ownSize = ownSize;
    }
  }

  private static Set<RichPackage> getRichPackages(Set<DpkgQuery.Package> dpkgQueryPackages) {
    Set<RichPackage> richPackages = createNakedRichPackages(dpkgQueryPackages);

    fillDepends(richPackages, dpkgQueryPackages);

    calcSizeWithDeps(richPackages);

    return richPackages;
  }

  private static Set<RichPackage> createNakedRichPackages(Set<DpkgQuery.Package> dpkgQueryPackages) {
    return dpkgQueryPackages.stream()
            .map(dpkgQueryPackage -> new RichPackage(dpkgQueryPackage.getName(), dpkgQueryPackage.getSize()))
            .collect(toSet());
  }

  private static void fillDepends(Set<RichPackage> richPackages, Set<DpkgQuery.Package> dpkgQueryPackages) {
    Map<String, Set<String>> replacementToReplacers = getNameToAlternatives(dpkgQueryPackages);
    Map<String, RichPackage> nameToRichPackage = richPackages.stream().collect(toMap(
            richPackage -> richPackage.name,
            identity()
    ));
    for (DpkgQuery.Package dpkgQueryPackage : dpkgQueryPackages) {
      Set<RichPackage> richDepends = dpkgQueryPackage.getDepends().stream()
              .map(packageName -> {
                RichPackage richPackage = getRichPackageOrReplacement(packageName, nameToRichPackage, replacementToReplacers);
                if (richPackage == null) {
                  System.out.println("Can not find replacement for " + packageName + " required for " + dpkgQueryPackage.getName());
                }
                return richPackage;
              })
              .filter(richPackage -> richPackage != null)
              .collect(toSet());
      RichPackage richPackage = nameToRichPackage.get(dpkgQueryPackage.getName());
      richPackage.depends = richDepends;
    }
  }

  private static Map<String, Set<String>> getNameToAlternatives(Set<DpkgQuery.Package> dpkgQueryPackages) {
    Map<String, Set<String>> nameToAlternatives = new HashMap<>();
    for (DpkgQuery.Package dpkgQueryPackage : dpkgQueryPackages) {
      fillNameToAlternatives(dpkgQueryPackage.getName(), dpkgQueryPackage.getReplaces(), nameToAlternatives);
      fillNameToAlternatives(dpkgQueryPackage.getName(), dpkgQueryPackage.getProvides(), nameToAlternatives);
    }
    return nameToAlternatives;
  }

  private static void fillNameToAlternatives(String alternative, Set<String> packages, Map<String, Set<String>> nameToAlternatives) {
    for (String packageName : packages) {
      Set<String> alternatives = nameToAlternatives.get(packageName);
      if (alternatives == null) {
        alternatives = new HashSet<>();
        nameToAlternatives.put(packageName, alternatives);
      }
      alternatives.add(alternative);
    }  
  }

  private static RichPackage getRichPackageOrReplacement(String name,
                                                         Map<String, RichPackage> nameToRichPackage,
                                                         Map<String, Set<String>> replacementToReplacers) {
    RichPackage richPackage = nameToRichPackage.get(name);
    if (richPackage != null) {
      return richPackage;
    }
    Set<String> replacers = replacementToReplacers.get(name);
    if (replacers != null) {
      for (String replacer : replacers) {
        richPackage = nameToRichPackage.get(replacer);
        if (richPackage != null) {
          return richPackage;
        }
      }
    }
    return null;
  }

  private static void calcSizeWithDeps(Set<RichPackage> richPackages) {
    for (RichPackage richPackage : richPackages) {
      calcSizeWithDeps(richPackage);
    }
  }

  private static void calcSizeWithDeps(RichPackage richPackage) {
    Set<RichPackage> allDeps = new HashSet<>();
    allDeps.add(richPackage);
    Queue<RichPackage> queue = new ArrayDeque<>();
    queue.add(richPackage);
    while (!queue.isEmpty()) {
      RichPackage currentPackage = queue.remove();
      for (RichPackage depend : currentPackage.depends) {
        if (!allDeps.contains(depend)) {
          allDeps.add(depend);
          queue.add(depend);
        }
      }
    }
    richPackage.sizeWithDeps = allDeps.stream().mapToInt(pack -> pack.ownSize).sum();
  }

  private static void printResults(Collection<RichPackage> richPackages) {
    int sizeAll = richPackages.stream().mapToInt(pack -> pack.ownSize).sum();

    System.out.println("Package OwnSize Percent SizeWithDeps Percent:");
    richPackages.stream()
            .sorted(comparingInt(pack -> pack.ownSize))
            .forEach(pack -> {
              float ownSizePercent = 100.0f * pack.ownSize / sizeAll;
              float sizeWithDepsPercent = 100.0f * pack.sizeWithDeps / sizeAll;
              String line = format("%s %d %.2f%% %d %.2f%%",
                      pack.name, pack.ownSize, ownSizePercent, pack.sizeWithDeps, sizeWithDepsPercent);
              System.out.println(line);
            });
  }

  private DpkgListMain() {
  }
}
