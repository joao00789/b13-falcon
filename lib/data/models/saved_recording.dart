class SavedRecording {
  const SavedRecording({
    required this.uri,
    required this.name,
    required this.sizeBytes,
    required this.duration,
    required this.dateAdded,
  });

  final String uri;
  final String name;
  final int sizeBytes;
  final Duration duration;
  final int dateAdded;

  factory SavedRecording.fromMap(Map<dynamic, dynamic> map) {
    return SavedRecording(
      uri: map['uri'] as String,
      name: map['name'] as String,
      sizeBytes: (map['sizeBytes'] as num?)?.toInt() ?? 0,
      duration: Duration(
        milliseconds: (map['durationMs'] as num?)?.toInt() ?? 0,
      ),
      dateAdded: (map['dateAdded'] as num?)?.toInt() ?? 0,
    );
  }
}
