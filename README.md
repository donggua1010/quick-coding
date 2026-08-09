# quick-coding

Generate a complete MyBatis stack (Entity / Mapper / Mapper XML / Service / Controller)
directly from database tables in IntelliJ IDEA **Ultimate Edition**'s Database tool window.

## Features

- Right-click one or more tables -> **Generate Code**
- Entity class (Lombok-annotated)
- Mapper interface + MyBatis Mapper XML (`resultMap`, dynamic `<where>`, CRUD)
  - MySQL: `ON DUPLICATE KEY UPDATE`
  - PostgreSQL: `ON CONFLICT ... DO UPDATE SET`
- Service interface + `ServiceImpl`
- REST Controller (optional Swagger annotations)
- Method-level navigation between Mapper Java methods and XML statements
- Multi-module aware: pick a target module, code/resource paths follow automatically
- Customizable FreeMarker templates (Settings -> Tools -> Code Templates)

## Requirements

- IntelliJ IDEA **Ultimate** 2024.2 – 2025.1 (Database tools plugin)
- JDK 17 for building

## Usage

1. Connect a data source (MySQL / PostgreSQL / MariaDB…) in the **Database** tool window.
2. Right-click a table (or multi-select several tables).
3. Choose **Generate Code**.
4. Configure module, base package (derived from source root path), prefixes and options.
5. Click OK — files are written into the selected source / resource roots.

## Development

```bash
gradle buildPlugin        # build the distributable zip
gradle runIde             # run a development IDE instance with the plugin
gradle test               # run tests
```

## Publishing to JetBrains Marketplace

See [PUBLISHING.md](PUBLISHING.md).

## License

[MIT](LICENSE)