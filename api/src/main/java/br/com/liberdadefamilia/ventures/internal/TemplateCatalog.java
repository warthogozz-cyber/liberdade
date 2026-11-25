package br.com.liberdadefamilia.ventures.internal;

import br.com.liberdadefamilia.ventures.domain.VentureType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** The tracks and the ready-made models. Values are in reais; the copy is PT-BR. */
public final class TemplateCatalog {

    public static final List<String> BLANK_STEPS =
            List.of("Anote os custos do começo e do mês", "Defina o preço de cada produto", "Faça a primeira venda");

    private static final Template FOOD_SWEETS = new Template(
            "food-sweets",
            "Doces por encomenda",
            VentureType.FOOD,
            List.of(
                    item("Panelas e utensílios", "350.00"),
                    item("Batedeira", "300.00"),
                    item("Embalagens e forminhas iniciais", "150.00")),
            List.of(
                    item("Gás", "90.00"),
                    item("Energia da cozinha", "60.00"),
                    item("Transporte das entregas", "70.00"),
                    item("Divulgação", "50.00")),
            List.of(product("Brigadeiro gourmet", "1.80", "4.50", 300)),
            List.of(
                    "Confira as regras da sua cidade para vender comida feita em casa",
                    "Teste a receita e anote o custo de cada unidade",
                    "Defina o preço e a embalagem",
                    "Faça uma primeira leva para amigos e família",
                    "Divulgue para vizinhos e grupos do bairro",
                    "Veja se o MEI faz sentido para você"));

    private static final Template DIGITAL_EBOOK = new Template(
            "digital-ebook",
            "E-book ou curso curto",
            VentureType.DIGITAL_PRODUCT,
            List.of(item("Revisão e capa", "200.00"), item("Programa de diagramação", "120.00")),
            List.of(item("Página de vendas", "40.00"), item("Divulgação", "60.00")),
            List.of(product("E-book", "2.00", "29.90", 20)),
            List.of(
                    "Escolha um tema que você conhece bem",
                    "Escreva um roteiro curto, do começo ao fim",
                    "Revise o texto e prepare a capa",
                    "Defina o preço e onde vai vender",
                    "Mostre um trecho para quem já pediu dicas",
                    "Peça a opinião dos primeiros leitores"));

    private static final Template CONTENT_CHANNEL = new Template(
            "content-channel",
            "Canal de conteúdo",
            VentureType.CONTENT,
            List.of(
                    item("Microfone", "250.00"),
                    item("Iluminação simples", "150.00"),
                    item("Programa de edição", "100.00")),
            List.of(item("Internet", "80.00"), item("Energia do estúdio", "30.00")),
            List.of(
                    product("Publicidade em vídeo", "20.00", "150.00", 2),
                    product("Aula gravada", "5.00", "49.90", 10)),
            List.of(
                    "Escolha o assunto e o público do canal",
                    "Monte um calendário simples de publicações",
                    "Grave e edite os primeiros conteúdos",
                    "Publique e anote o que chamou atenção",
                    "Defina o que vai oferecer: parcerias ou aulas",
                    "Confira as regras da plataforma antes de cobrar"));

    private static final Template RESALE_GOODS = new Template(
            "resale-goods",
            "Revenda de produtos",
            VentureType.RESALE,
            List.of(
                    item("Estoque inicial", "600.00"),
                    item("Sacolas e etiquetas", "80.00"),
                    item("Prateleira e organização", "120.00")),
            List.of(item("Transporte", "80.00"), item("Divulgação", "50.00")),
            List.of(product("Camiseta lisa", "25.00", "45.00", 25), product("Boné", "15.00", "30.00", 10)),
            List.of(
                    "Pesquise fornecedores e compare os preços",
                    "Compre uma quantidade pequena para testar",
                    "Calcule o preço somando todos os custos",
                    "Fotografe os produtos e monte o catálogo",
                    "Combine a forma de entrega e de pagamento",
                    "Anote o que vendeu e ajuste o estoque",
                    "Veja se o MEI faz sentido para você"));

    private static final Template SERVICE_HOURS = new Template(
            "service-hours",
            "Serviço por atendimento",
            VentureType.SERVICE,
            List.of(item("Materiais de trabalho", "300.00"), item("Cartões e divulgação inicial", "100.00")),
            List.of(item("Transporte", "120.00"), item("Divulgação", "40.00"), item("Reposição de materiais", "60.00")),
            List.of(product("Atendimento de uma hora", "8.00", "60.00", 16)),
            List.of(
                    "Defina qual serviço você oferece e para quem",
                    "Calcule quanto custa cada atendimento",
                    "Defina o preço da hora de trabalho",
                    "Monte sua agenda de horários",
                    "Avise conhecidos e peça indicações",
                    "Anote cada atendimento e o que sobrou"));

    private static final List<Track> TRACKS = List.of(
            new Track("food", "Culinária", "Venda o que você sabe cozinhar.", false, List.of(FOOD_SWEETS)),
            new Track(
                    "digital",
                    "Produtos digitais",
                    "Crie uma vez e venda várias vezes.",
                    false,
                    List.of(DIGITAL_EBOOK)),
            new Track(
                    "content",
                    "Conteúdo",
                    "Transforme o que você sabe em vídeos, textos e aulas.",
                    false,
                    List.of(CONTENT_CHANNEL)),
            new Track(
                    "resale-services",
                    "Revenda e serviços",
                    "Revenda produtos ou venda o seu tempo.",
                    false,
                    List.of(RESALE_GOODS, SERVICE_HOURS)),
            new Track(
                    "investments",
                    "Investimentos (conteúdo educativo)",
                    "Entenda como cada opção funciona e simule cenários.",
                    true,
                    List.of()));

    private TemplateCatalog() {}

    public static List<Track> tracks() {
        return TRACKS;
    }

    public static Optional<Template> template(String key) {
        return TRACKS.stream()
                .flatMap(track -> track.templates().stream())
                .filter(template -> template.key().equals(key))
                .findFirst();
    }

    private static TemplateItem item(String name, String amount) {
        return new TemplateItem(name, new BigDecimal(amount));
    }

    private static TemplateProduct product(String name, String cost, String price, int units) {
        return new TemplateProduct(name, new BigDecimal(cost), new BigDecimal(price), units);
    }
}
