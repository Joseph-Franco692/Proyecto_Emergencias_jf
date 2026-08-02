migrate((app) => {
  try {
    app.findCollectionByNameOrId("evidencias_archivo")
    return
  } catch (_) {
    // La colecciÃ³n todavÃ­a no existe y se crea a continuaciÃ³n.
  }

  const collection = new Collection({
    type: "base",
    name: "evidencias_archivo",
    listRule: null,
    viewRule: null,
    createRule: null,
    updateRule: null,
    deleteRule: null,
    fields: [
      {
        type: "file",
        name: "archivo",
        required: true,
        maxSelect: 1,
        maxSize: 15728640,
        mimeTypes: ["image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm"],
        thumbs: [],
        protected: false
      },
      { type: "text", name: "sha256", required: true, min: 64, max: 64, pattern: "" },
      { type: "number", name: "reporteId", required: true, min: 1, max: null, onlyInt: true },
      { type: "text", name: "mimeType", required: true, min: 1, max: 100, pattern: "" },
      { type: "number", name: "tamanoBytes", required: true, min: 1, max: null, onlyInt: true }
    ],
    indexes: ["CREATE INDEX idx_evidencias_reporte_id ON evidencias_archivo (reporteId)"]
  })

  app.save(collection)
}, (app) => {
  try {
    const collection = app.findCollectionByNameOrId("evidencias_archivo")
    app.delete(collection)
  } catch (_) {}
})
