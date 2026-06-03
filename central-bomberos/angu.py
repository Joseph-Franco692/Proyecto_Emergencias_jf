import os

# Nombre del archivo de salida
archivo_salida = "contexto_angular.txt"

# Extensiones de archivos de Angular que nos interesan para la lógica y diseño
extensiones_validas = {'.ts', '.html', '.css'}

# Carpetas pesadas o autogeneradas que ignoraremos por completo
carpetas_a_ignorar = {'.git', '.angular', '.vscode', 'node_modules', 'public'}

with open(archivo_salida, 'w', encoding='utf-8') as salida:
    salida.write("=========================================================\n")
    salida.write("     ESTRUCTURA Y CÓDIGO FUENTE - PROYECTO ANGULAR\n")
    salida.write("=========================================================\n\n")
    
    for raiz, dirs, archivos in os.walk('.'):
        # Filtrar carpetas a ignorar
        dirs[:] = [d for d in dirs if d not in carpetas_a_ignorar]
        
        for archivo in archivos:
            # Ignoramos los archivos de pruebas unitarias (.spec.ts) para limpiar contexto
            if archivo.endswith('.spec.ts'):
                continue
                
            nombre_completo, extension = os.path.splitext(archivo)
            if extension in extensiones_validas:
                ruta_relativa = os.path.relpath(os.path.join(raiz, archivo), '.')
                
                salida.write(f"\n/* ######################################################### */\n")
                salida.write(f"/* ARCHIVO: {ruta_relativa} */\n")
                salida.write(f"/* ######################################################### */\n\n")
                
                try:
                    with open(os.path.join(raiz, archivo), 'r', encoding='utf-8') as f_entrada:
                        salida.write(f_entrada.read())
                except Exception as e:
                    salida.write(f"/* [ERROR AL LEER ARCHIVO]: {str(e)} */\n")
                salida.write("\n")

print(f"¡Listo! Se ha generado el archivo '{archivo_salida}' con todo tu código de Angular.")