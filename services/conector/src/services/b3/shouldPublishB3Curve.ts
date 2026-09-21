function shouldPublishCurve(record: { knownType?: boolean }): boolean {
  if (record.knownType) {
    return true;
  }

  return process.env.PUBLISH_UNKNOWN_TYPES === "true";
}

module.exports = {
  shouldPublishCurve,
};

export {};
