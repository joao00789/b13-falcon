import 'dart:io';

import 'package:path_provider/path_provider.dart';

class StorageService {
  Future<Directory> getMovieDirectory() async {
    final baseDir = await getExternalStorageDirectory();
    final root = baseDir ?? await getApplicationDocumentsDirectory();
    final moviesDir = Directory('${root.path}/Movies/B13 Falcon');
    if (!moviesDir.existsSync()) {
      moviesDir.createSync(recursive: true);
    }
    return moviesDir;
  }

  Future<double> getAvailableStorageMb() async {
    final dir = await getExternalStorageDirectory();
    if (dir == null) {
      return 0;
    }
    final stat = await dir.stat();
    final freeBytes = stat.size; // placeholder for available storage estimate
    return freeBytes / (1024 * 1024);
  }

  Future<int> totalStorageBytes() async {
    final dir = await getExternalStorageDirectory();
    if (dir == null) {
      return 0;
    }
    final files = dir.listSync(recursive: true, followLinks: false);
    int total = 0;
    for (final entity in files) {
      if (entity is File) {
        total += entity.lengthSync();
      }
    }
    return total;
  }
}
